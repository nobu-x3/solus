#include "memory/database.h"
#include <filesystem>
#include <fstream>
#include <iostream>
#include <nlohmann/json.hpp>

using json = nlohmann::json;
namespace fs = std::filesystem;

namespace solus {

MemoryDatabase::MemoryDatabase(const std::string &db_path, int dimension,
                               int max_elements)
    : m_DbPath(db_path), m_Dimension(dimension), m_MaxElements(max_elements) {
  fs::create_directories(m_DbPath);
}

MemoryDatabase::~MemoryDatabase() { save_index(); }

bool MemoryDatabase::initialize() {
  std::cout << "Initializing memory database..." << std::endl;
  m_Space = std::make_unique<hnswlib::InnerProductSpace>(m_Dimension);
  std::string index_path = m_DbPath + "/index.bin";
  std::string entries_path = m_DbPath + "/entries.json";
  if (fs::exists(index_path) && fs::exists(entries_path)) {
    std::cout << "Loading existing memory index..." << std::endl;
    load_index();
  } else {
    std::cout << "Creating new memory index..." << std::endl;
    m_Index = std::make_unique<hnswlib::HierarchicalNSW<float>>(
        m_Space.get(), m_MaxElements, 16, 200);
  }
  std::cout << "Memory database initialized with " << m_Entries.size()
            << " entries" << std::endl;
  return true;
}

void MemoryDatabase::add_entry(const MemoryEntry &entry,
                               const std::vector<float> &embedding) {
  std::lock_guard<std::mutex> lock(m_DbMutex);
  if (embedding.size() != static_cast<size_t>(m_Dimension)) {
    std::cerr << "Embedding dimension mismatch: expected " << m_Dimension
              << ", got " << embedding.size() << std::endl;
    return;
  }
  size_t id = m_Entries.size();
  m_Entries.push_back(entry);
  try {
    m_Index->addPoint(embedding.data(), id);
  } catch (const std::exception &e) {
    std::cerr << "Failed to add memory to index: " << e.what() << std::endl;
    m_Entries.pop_back();
  }
}

std::vector<MemoryEntry>
MemoryDatabase::search_entries(const std::vector<float> &query_embedding,
                               const std::string &user_id, int k) {
  std::lock_guard<std::mutex> lock(m_DbMutex);
  if (m_Entries.empty()) {
    return {};
  }
  if (query_embedding.size() != static_cast<size_t>(m_Dimension)) {
    std::cerr << "Query embedding dimension mismatch" << std::endl;
    return {};
  }
  // Search for k*2 to account for filtering
  int search_k = std::min(k * 2, static_cast<int>(m_Entries.size()));
  try {
    auto result = m_Index->searchKnn(query_embedding.data(), search_k);
    std::vector<MemoryEntry> results;
    results.reserve(k);
    // Extract results and filter by user_id
    while (!result.empty() && results.size() < static_cast<size_t>(k)) {
      auto [dist, id] = result.top();
      result.pop();
      if (id < m_Entries.size() && m_Entries[id].user_id == user_id) {
        results.push_back(m_Entries[id]);
      }
    }
    return results;
  } catch (const std::exception &e) {
    std::cerr << "Memory search failed: " << e.what() << std::endl;
    return {};
  }
}

void MemoryDatabase::save_index() {
  std::lock_guard<std::mutex> lock(m_DbMutex);
  if (!m_Index || m_Entries.empty()) {
    return;
  }
  try {
    std::cout << "Saving memory database..." << std::endl;
    std::string index_path = m_DbPath + "/index.bin";
    m_Index->saveIndex(index_path);
    std::string entries_path = m_DbPath + "/entries.json";
    std::ofstream out(entries_path);
    json j = json::array();
    for (const auto &entry : m_Entries) {
      j.push_back({{"user_id", entry.user_id},
                   {"conversation_id", entry.conversation_id},
                   {"text", entry.text},
                   {"timestamp", entry.timestamp}});
    }
    out << j.dump(2);
    out.close();
    std::cout << "Memory database saved (" << m_Entries.size() << " entries)"
              << std::endl;
  } catch (const std::exception &e) {
    std::cerr << "Failed to save memory database: " << e.what() << std::endl;
  }
}

void MemoryDatabase::load_index() {
  std::lock_guard<std::mutex> lock(m_DbMutex);
  try {
    std::string index_path = m_DbPath + "/index.bin";
    m_Index = std::make_unique<hnswlib::HierarchicalNSW<float>>(m_Space.get(),
                                                                index_path);
    std::string entries_path = m_DbPath + "/entries.json";
    std::ifstream in(entries_path);
    if (!in.good()) {
      std::cerr << "Failed to open entries file" << std::endl;
      return;
    }
    json j;
    in >> j;
    in.close();
    m_Entries.clear();
    m_Entries.reserve(j.size());
    for (const auto &item : j) {
      MemoryEntry entry;
      entry.user_id = item["user_id"].get<std::string>();
      entry.conversation_id = item["conversation_id"].get<std::string>();
      entry.text = item["text"].get<std::string>();
      entry.timestamp = item["timestamp"].get<int64_t>();
      m_Entries.push_back(entry);
    }
    std::cout << "Loaded " << m_Entries.size() << " memory entries"
              << std::endl;
  } catch (const std::exception &e) {
    std::cerr << "Failed to load memory database: " << e.what() << std::endl;
  }
}

} // namespace solus