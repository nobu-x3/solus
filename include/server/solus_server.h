#pragma once

#include "llm/llama_handler.h"
#include "memory/database.h"
#include "server/config.h"
#include "server/prompt_builder.h"
#include <memory>
#include <net/http.h>

namespace solus {

class SolusServer {
public:
  explicit SolusServer(const ServerConfig &config);
  ~SolusServer();

  // Delete copy/move constructors
  SolusServer(const SolusServer &) = delete;
  SolusServer &operator=(const SolusServer &) = delete;
  SolusServer(SolusServer &&) = delete;
  SolusServer &operator=(SolusServer &&) = delete;

  bool initialize();
  void run();
  void stop();

private:
  void setupRoutes();

  http::Response handle_health(const http::Request &req);
  http::Response handle_chat(const http::Request &req);
  http::Response handle_memory_clear(const http::Request &req);

  ServerConfig m_Config;
  std::unique_ptr<LlamaHandler> m_Llama;
  std::unique_ptr<MemoryDatabase> m_MemoryDb;
  std::unique_ptr<PromptBuilder> m_PromptBuilder;
  std::unique_ptr<http::Server> m_HttpServer;
};

} // namespace solus