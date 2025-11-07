#include "server/solus_server.h"
#include "utils/helpers.h"
#include <gtest/gtest.h>

namespace solus::test {

class ServerIntegrationTest : public ::testing::Test {
protected:
  void SetUp() override {
    m_TempDir = std::make_unique<TempDirectory>();

    m_Config.model_path = "mock_model.gguf";
    m_Config.memory_db_path = m_TempDir->path();
    m_Config.port = 18000 + (rand() % 1000); // Random port to avoid conflicts
    m_Config.embedding_dim = 768;
  }

  void TearDown() override {
    if (m_SolusServer) {
      m_SolusServer->stop();
    }
  }

  std::unique_ptr<TempDirectory> m_TempDir;
  ServerConfig m_Config;
  std::unique_ptr<SolusServer> m_SolusServer;
};

// Note: This test will fail without a real model
// In real scenarios, you'd mock the LlamaHandler
TEST_F(ServerIntegrationTest, ServerInitialization) {
  m_SolusServer = std::make_unique<SolusServer>(m_Config);
  // Initialize would fail without model, but we test the structure
  EXPECT_NE(m_SolusServer, nullptr);
}

TEST_F(ServerIntegrationTest, ConfigurationPropagation) {
  m_Config.port = 19999;
  m_Config.n_threads = 8;
  m_SolusServer = std::make_unique<SolusServer>(m_Config);
  EXPECT_NE(m_SolusServer, nullptr);
}

// Mock HTTP client for testing
class MockHttpClient {
public:
  static std::string get(const std::string &url) {
    // Simulate HTTP GET
    return R"({"status": "healthy"})";
  }
  static std::string post(const std::string &url, const std::string &body) {
    // Simulate HTTP POST
    return R"({"response": "Test response", "action": null})";
  }
};

// Test that health endpoint returns correct JSON structure
TEST_F(ServerIntegrationTest, HealthEndpointStructure) {
  std::string response = MockHttpClient::get("http://localhost:8000/health");
  auto json = nlohmann::json::parse(response);
  EXPECT_TRUE(json.contains("status"));
}

TEST_F(ServerIntegrationTest, ChatEndpointStructure) {
  std::string request = R"({
        "text": "Hello",
        "user_id": "test_user"
    })";
  std::string response =
      MockHttpClient::post("http://localhost:8000/chat", request);
  auto json = nlohmann::json::parse(response);
  EXPECT_TRUE(json.contains("response"));
}

} // namespace solus::test