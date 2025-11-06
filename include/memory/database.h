#pragma once

#include <cstdint>
#include <hnswlib.h>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace solus {

struct MemoryEntry {
  std::string user_id;
  std::string conversation_id;
  std::string text;
  int64_t timestamp;

  MemoryEntry() : timestamp(0) {}

  MemoryEntry(const std::string &uid, const std::string &cid,
              const std::string &txt, int64_t ts)
      : user_id(uid), conversation_id(cid), text(txt), timestamp(ts) {}
};

class MemoryDatabase {
public:
  MemoryDatabase(const std::string &db_path, int dimension,
                 int max_elements = 1000000);
  ~MemoryDatabase();

  // Delete copy/move constructors
  MemoryDatabase(const MemoryDatabase &) = delete;
  MemoryDatabase &operator=(const MemoryDatabase &) = delete;
  MemoryDatabase(MemoryDatabase &&) = delete;
  MemoryDatabase &operator=(MemoryDatabase &&) = delete;

  bool initialize();

  void add_entry(const MemoryEntry &entry, const std::vector<float> &embedding);

  std::vector<MemoryEntry>
  search_entries(const std::vector<float> &query_embedding,
                 const std::string &user_id, int k = 5);

  void save_index();
  void load_index();

  size_t get_entry_count() const { return m_Entries.size(); }

private:
  std::string m_DbPath;
  int m_Dimension;
  int m_MaxElements;

  std::unique_ptr<hnswlib::HierarchicalNSW<float>> m_Index;
  std::unique_ptr<hnswlib::InnerProductSpace> m_Space;

  std::vector<MemoryEntry> m_Entries;
  std::mutex m_DbMutex;
};
} // namespace solus