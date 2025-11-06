#pragma once

#include "llama.h"
#include "server/config.h"
#include <mutex>
#include <string>
#include <vector>

namespace solus {

struct GenerationParams {
  float temperature = 0.7f;
  float top_p = 0.9f;
  float top_k = 40.0f;
  int max_tokens = 1024;
  int repeat_last_n = 64;
  float repeat_penalty = 1.1f;
};

class LlamaHandler {
public:
  explicit LlamaHandler(const ServerConfig &config);
  ~LlamaHandler();

  LlamaHandler(const LlamaHandler &) = delete;
  LlamaHandler &operator=(const LlamaHandler &) = delete;
  LlamaHandler(LlamaHandler &&) = delete;
  LlamaHandler &operator=(LlamaHandler &&) = delete;

  bool initialize();

  std::string generate(const std::string &prompt,
                       const GenerationParams &params);
  std::vector<float> get_embedding(const std::string &text);

  bool is_initialized() const { return m_Model != nullptr && m_Ctx != nullptr; }
  int get_context_size() const { return m_Config.n_ctx; }
  int get_embedding_dim() const;

private:
  std::vector<llama_token> tokenize(const std::string &text,
                                    bool add_bos = true);
  std::string detokenize(const std::vector<llama_token> &tokens);

  ServerConfig m_Config;
  llama_model *m_Model;
  llama_context *m_Ctx;
  std::mutex m_InterferenceMutex;
};

} // namespace solus