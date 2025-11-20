#include "server/solus_server.h"
#include "net/http.h"
#include "server/prompt_builder.h"
#include "server/response_parser.h"
#include <chrono>
#include <iostream>
#include <nlohmann/json.hpp>

using json = nlohmann::json;

namespace solus {

SolusServer::SolusServer(const ServerConfig &config) : m_Config(config) {}

SolusServer::~SolusServer() {
  if (m_MemoryDb) {
    m_MemoryDb->save_index();
  }
}

bool SolusServer::initialize() {
  std::cout << "Initializing Solus Server..." << std::endl;
  m_Llama = std::make_unique<LlamaHandler>(m_Config);
  if (!m_Llama->initialize()) {
    std::cerr << "Failed to initialize LLM" << std::endl;
    return false;
  }
  m_Config.embedding_dim = m_Llama->get_embedding_dim();
  m_MemoryDb = std::make_unique<MemoryDatabase>(
      m_Config.memory_db_path, m_Config.embedding_dim, m_Config.max_memories);
  if (!m_MemoryDb->initialize()) {
    std::cerr << "Failed to initialize memory database" << std::endl;
    return false;
  }
  m_PromptBuilder = std::make_unique<PromptBuilder>();
  http::ServerConfig http_cfg;
  http_cfg.is_multithreaded = m_Config.worker_threads > 1;
  http_cfg.port = m_Config.port;
  m_HttpServer = std::make_unique<http::Server>(http_cfg);
  m_HttpServer->start();
  setup_routes();
  std::cout << "Server initialization complete!" << std::endl;
  return true;
}

void SolusServer::setup_routes() {
  m_HttpServer->route(
      "/health", http::EMethod::GET,
      [this](const http::Request &req) { return this->handle_health(req); });
  m_HttpServer->route(
      "/chat", http::EMethod::POST,
      [this](const http::Request &req) { return this->handle_chat(req); });
  m_HttpServer->route("/memory/clear", http::EMethod::POST,
                      [this](const http::Request &req) {
                        return this->handle_memory_clear(req);
                      });
}

http::Response SolusServer::handle_health(const http::Request &req) {
  json response = {{"status", "healthy"},
                   {"model_loaded", m_Llama->is_initialized()},
                   {"memory_count", m_MemoryDb->get_entry_count()},
                   {"embedding_dim", m_Config.embedding_dim}};
  http::Response res;
  res.status_code = 200;
  res.body = response.dump();
  res.headers.set("Content-Type", "application/json");
  return res;
}

http::Response SolusServer::handle_chat(const http::Request &req) {
  auto start_time = std::chrono::high_resolution_clock::now();
  try {
    json body = json::parse(req.body);
    std::string text = body["text"].get<std::string>();
    std::string user_id = body["user_id"].get<std::string>();
    std::string conversation_id = body.value(
        "conversation_id", user_id + "_" + std::to_string(std::time(nullptr)));
    auto query_embedding = m_Llama->get_embedding(text);
    if (query_embedding.empty()) {
      throw std::runtime_error("Failed to generate embedding");
    }
    auto memories = m_MemoryDb->search_entries(query_embedding, user_id, 5);
    std::string prompt = m_PromptBuilder->build_chat_prompt(
        text, memories, PromptBuilder::EPromptFormat::QWEN);
    GenerationParams gen_params;
    gen_params.temperature = m_Config.temperature;
    gen_params.top_p = m_Config.top_p;
    gen_params.top_k = m_Config.top_k;
    gen_params.max_tokens = m_Config.max_tokens;
    gen_params.repeat_last_n = m_Config.repeat_last_n;
    gen_params.repeat_penalty = m_Config.repeat_penalty;
    std::string response_text = m_Llama->generate(prompt, gen_params);
    if (response_text.empty()) {
      throw std::runtime_error("Empty response from LLM");
    }
    auto parsed = ResponseParser::parse_response(response_text);
    MemoryEntry new_memory(user_id, conversation_id,
                           "User: " + text + "\nSolus: " + parsed.response,
                           std::time(nullptr));
    m_MemoryDb->add_entry(new_memory, query_embedding);
    json result = {{"action", parsed.action.empty()
                                  ? nullptr
                                  : json::parse(parsed.action)},
                   {"response", parsed.response},
                   {"conversation_id", conversation_id}};
    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(
                        end_time - start_time)
                        .count();
    if (m_Config.verbose) {
      std::cout << "Chat request processed in " << duration << "ms"
                << std::endl;
    }
    http::Response res;
    res.status_code = 200;
    res.body = result.dump();
    res.headers.set("Content-Type", "application/json");
    return res;
  } catch (const json::exception &e) {
    std::cerr << "JSON error: " << e.what() << std::endl;
    json error = {{"error", "Invalid JSON format"}};
    http::Response res;
    res.status_code = 400;
    res.body = error.dump();
    res.headers.set("Content-Type", "application/json");
    return res;
  } catch (const std::exception &e) {
    std::cerr << "Error processing chat: " << e.what() << std::endl;
    json error = {{"error", e.what()}};
    http::Response res;
    res.status_code = 500;
    res.body = error.dump();
    res.headers.set("Content-Type", "application/json");
    return res;
  }
}

http::Response SolusServer::handle_memory_clear(const http::Request &) {
  json response = {{"status", "Memory clearing not implemented yet"},
                   {"message", "Feature coming soon"}};
  http::Response res;
  res.status_code = 200;
  res.body = response.dump();
  res.headers.set("Content-Type", "application/json");
  return res;
}

void SolusServer::run() {
  std::cout << "Starting server on " << m_Config.host << ":" << m_Config.port
            << std::endl;
  m_HttpServer->run();
}

void SolusServer::stop() {
  std::cout << "Stopping server..." << std::endl;
  if (m_HttpServer) {
    m_HttpServer->stop();
  }
  if (m_MemoryDb) {
    m_MemoryDb->save_index();
  }
}

} // namespace solus
