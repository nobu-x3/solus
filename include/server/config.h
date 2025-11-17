#pragma once

#include <cstdint>
#include <string>

namespace solus {

struct ServerConfig {
  // Model settings
  std::string model_path = "./models/qwen2.5-14b-instruct-q4_k_m.gguf";
  int n_ctx = 4096;
  int n_threads = 16;
  int n_gpu_layers = 33;
  int n_batch = 512;

  // Generation settings
  float temperature = 0.7f;
  float top_p = 0.9f;
  float top_k = 40.0f;
  int max_tokens = 1024;
  int repeat_last_n = 64;
  float repeat_penalty = 1.1f;

  // Server settings
  uint16_t port = 8000;
  std::string host = "0.0.0.0";
  int worker_threads = 4;

  // Memory database settings
  std::string memory_db_path = "./memory_db";
  int embedding_dim = 4096; // Qwen2.5 embedding size
  int max_memories = 1000;

  // Logging
  bool verbose = true;
  std::string log_file = "./solus.log";
};
} // namespace solus