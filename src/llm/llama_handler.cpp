#include "llm/llama_handler.h"
#include "llama.h"
#include <iostream>

namespace solus {

LlamaHandler::LlamaHandler(const ServerConfig &config)
    : m_Config(config), m_Model(nullptr), m_Ctx(nullptr) {}

LlamaHandler::~LlamaHandler() {
  if (m_Ctx) {
    llama_free(m_Ctx);
    m_Ctx = nullptr;
  }
  if (m_Model) {
    llama_model_free(m_Model);
    m_Model = nullptr;
  }
  llama_backend_free();
}

bool LlamaHandler::initialize() {
  std::cout << "Initializing llama.cpp backend..." << std::endl;
  llama_backend_init();
  llama_model_params model_params = llama_model_default_params();
  model_params.n_gpu_layers = m_Config.n_gpu_layers;
  std::cout << "Loading model from: " << m_Config.model_path << std::endl;
  m_Model =
      llama_model_load_from_file(m_Config.model_path.c_str(), model_params);
  if (!m_Model) {
    std::cerr << "Failed to load model from: " << m_Config.model_path
              << std::endl;
    return false;
  }
  // Setup context parameters
  llama_context_params ctx_params = llama_context_default_params();
  ctx_params.n_ctx = m_Config.n_ctx;
  ctx_params.n_batch = m_Config.n_batch;
  ctx_params.n_threads = m_Config.n_threads;
  ctx_params.n_threads_batch = m_Config.n_threads;
  ctx_params.embeddings = true;
  ctx_params.pooling_type = LLAMA_POOLING_TYPE_MEAN;
  m_Ctx = llama_init_from_model(m_Model, ctx_params);
  if (!m_Ctx) {
    std::cerr << "Failed to create llama context" << std::endl;
    return false;
  }
  std::cout << "Model loaded successfully!" << std::endl;
  std::cout << "  Context size: " << m_Config.n_ctx << std::endl;
  std::cout << "  Embedding size: " << llama_model_n_embd(m_Model) << std::endl;
  std::cout << "  GPU layers: " << m_Config.n_gpu_layers << std::endl;
  return true;
}

std::vector<llama_token> LlamaHandler::tokenize(const std::string &text,
                                                bool add_bos) {
  const llama_vocab *vocab = llama_model_get_vocab(m_Model);
  const int n_tokens_max = text.size() + (add_bos ? 1 : 0) + 1;
  std::vector<llama_token> tokens(n_tokens_max);
  const int n_tokens =
      llama_tokenize(vocab, text.c_str(), text.size(), tokens.data(),
                     tokens.size(), add_bos, false);
  if (n_tokens < 0) {
    tokens.resize(-n_tokens);
    const int check =
        llama_tokenize(vocab, text.c_str(), text.size(), tokens.data(),
                       tokens.size(), add_bos, false);
    if (check < 0) {
      std::cerr << "Tokenization failed" << std::endl;
      return {};
    }
  }
  tokens.resize(n_tokens < 0 ? -n_tokens : n_tokens);
  return tokens;
}

std::string LlamaHandler::detokenize(const std::vector<llama_token> &tokens) {
  const llama_vocab *vocab = llama_model_get_vocab(m_Model);
  std::string result;
  result.reserve(tokens.size() * 4);
  for (const auto &token : tokens) {
    char buf[128];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    if (n > 0 && n < static_cast<int>(sizeof(buf))) {
      result.append(buf, n);
    }
  }
  return result;
}

std::string LlamaHandler::generate(const std::string &prompt,
                                   const GenerationParams &params) {
  std::lock_guard<std::mutex> lock(m_InterferenceMutex);
  // Tokenize prompt
  auto tokens = tokenize(prompt, true);
  if (tokens.empty()) {
    std::cerr << "Failed to tokenize prompt" << std::endl;
    return "";
  }
  // Check context size
  if (static_cast<int>(tokens.size()) >= m_Config.n_ctx) {
    std::cerr << "Prompt too long: " << tokens.size()
              << " tokens (max: " << m_Config.n_ctx << ")" << std::endl;
    return "";
  }
  llama_memory_t mem = llama_get_memory(m_Ctx);
  llama_memory_clear(mem, false);
  llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
  if (llama_decode(m_Ctx, batch) != 0) {
    std::cerr << "Failed to decode prompt" << std::endl;
    return "";
  }
  auto sparams = llama_sampler_chain_default_params();
  llama_sampler *smpl = llama_sampler_chain_init(sparams);
  llama_sampler_chain_add(
      smpl, llama_sampler_init_top_k(static_cast<int>(params.top_k)));
  llama_sampler_chain_add(smpl, llama_sampler_init_top_p(params.top_p, 1));
  llama_sampler_chain_add(smpl, llama_sampler_init_temp(params.temperature));
  llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
  std::vector<llama_token> generated_tokens;
  generated_tokens.reserve(params.max_tokens);
  int n_decode = 0;
  const llama_vocab *vocab = llama_model_get_vocab(m_Model);
  while (n_decode < params.max_tokens) {
    const llama_token new_token = llama_sampler_sample(smpl, m_Ctx, -1);
    if (llama_vocab_is_eog(vocab, new_token)) {
      break;
    }
    generated_tokens.push_back(new_token);
    llama_token new_token_mut = new_token;
    llama_batch batch_next = llama_batch_get_one(&new_token_mut, 1);
    if (llama_decode(m_Ctx, batch_next) != 0) {
      std::cerr << "Failed to decode token" << std::endl;
      break;
    }
    n_decode++;
  }
  llama_sampler_free(smpl);
  return detokenize(generated_tokens);
}

std::vector<float> LlamaHandler::get_embedding(const std::string &text) {
  std::lock_guard<std::mutex> lock(m_InterferenceMutex);
  auto tokens = tokenize(text, true);
  if (tokens.empty()) {
    return {};
  }
  llama_memory_t mem = llama_get_memory(m_Ctx);
  llama_memory_clear(mem, false);
  llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
  if (llama_decode(m_Ctx, batch) != 0) {
    std::cerr << "Failed to decode for embeddings" << std::endl;
    return {};
  }
  const int n_embd = llama_model_n_embd(m_Model);
  const float *embd = llama_get_embeddings_seq(m_Ctx, 0);
  if (!embd) {
    std::cerr << "Failed to get embeddings" << std::endl;
    return {};
  }
  std::vector<float> embedding(embd, embd + n_embd);
  return embedding;
}

int LlamaHandler::get_embedding_dim() const {
  if (m_Model) {
    return llama_model_n_embd(m_Model);
  }
  return 0;
}

} // namespace solus
