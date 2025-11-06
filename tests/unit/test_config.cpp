#include <server/config.h>
#include <gtest/gtest.h>
#include <string>

namespace solus::test {

class ConfigTest : public ::testing::Test {
protected:
  ServerConfig config;
};

TEST_F(ConfigTest, DefaultValues) {
  EXPECT_EQ(config.port, 8000);
  EXPECT_EQ(config.n_threads, 16);
  EXPECT_EQ(config.n_ctx, 4096);
  EXPECT_EQ(config.n_gpu_layers, 33);
  EXPECT_FLOAT_EQ(config.temperature, 0.7f);
  EXPECT_EQ(config.host, "0.0.0.0");
}

TEST_F(ConfigTest, ModifyValues) {
  config.port = 9000;
  config.temperature = 0.5f;

  EXPECT_EQ(config.port, 9000);
  EXPECT_FLOAT_EQ(config.temperature, 0.5f);
}

TEST_F(ConfigTest, ModelPathValidation) {
  EXPECT_FALSE(config.model_path.empty());
  EXPECT_NE(config.model_path.find(".gguf"), std::string::npos);
}

} // namespace solus::test