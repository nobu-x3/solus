#include "memory/database.h"
#include "server/prompt_builder.h"
#include "utils/helpers.h"
#include <gtest/gtest.h>

namespace solus::test {

class PromptBuilderTest : public ::testing::Test {
protected:
  PromptBuilder builder;
};

TEST_F(PromptBuilderTest, BasicPromptGeneration) {
  std::vector<MemoryEntry> empty_memories;
  std::string prompt = builder.build_chat_prompt(
      "Hello", empty_memories, PromptBuilder::EPromptFormat::QWEN);
  EXPECT_FALSE(prompt.empty());
  EXPECT_TRUE(StringUtils::contains(prompt, "Hello"));
  EXPECT_TRUE(StringUtils::contains(prompt, "<|im_start|>"));
  EXPECT_TRUE(StringUtils::contains(prompt, "<|im_end|>"));
}

TEST_F(PromptBuilderTest, PromptWithMemories) {
  std::vector<MemoryEntry> memories;
  memories.emplace_back("user1", "conv1", "Previous conversation", 123456);
  memories.emplace_back("user1", "conv1", "Another memory", 123457);
  std::string prompt = builder.build_chat_prompt(
      "New message", memories, PromptBuilder::EPromptFormat::QWEN);
  EXPECT_TRUE(StringUtils::contains(prompt, "Previous conversation"));
  EXPECT_TRUE(StringUtils::contains(prompt, "Another memory"));
  EXPECT_TRUE(StringUtils::contains(prompt, "New message"));
}

TEST_F(PromptBuilderTest, PromptWithoutMemories) {
  std::vector<MemoryEntry> empty_memories;
  std::string prompt = builder.build_chat_prompt(
      "Message", empty_memories, PromptBuilder::EPromptFormat::QWEN);
  EXPECT_TRUE(StringUtils::contains(prompt, "No previous context"));
}

TEST_F(PromptBuilderTest, SystemPromptIncluded) {
  std::vector<MemoryEntry> empty_memories;
  std::string prompt = builder.build_chat_prompt(
      "Test", empty_memories, PromptBuilder::EPromptFormat::QWEN);
  EXPECT_TRUE(StringUtils::contains(prompt, "Solus"));
  EXPECT_TRUE(StringUtils::contains(prompt, "action"));
  EXPECT_TRUE(StringUtils::contains(prompt, "todo_add"));
}

TEST_F(PromptBuilderTest, PromptFormat) {
  std::vector<MemoryEntry> empty_memories;
  std::string prompt = builder.build_chat_prompt(
      "Test", empty_memories, PromptBuilder::EPromptFormat::QWEN);
  // Check Qwen chat format
  size_t system_start = prompt.find("<|im_start|>system");
  size_t system_end = prompt.find("<|im_end|>", system_start);
  size_t user_start = prompt.find("<|im_start|>user");
  size_t user_end = prompt.find("<|im_end|>", user_start);
  size_t assistant_start = prompt.find("<|im_start|>assistant");
  EXPECT_NE(system_start, std::string::npos);
  EXPECT_NE(system_end, std::string::npos);
  EXPECT_NE(user_start, std::string::npos);
  EXPECT_NE(user_end, std::string::npos);
  EXPECT_NE(assistant_start, std::string::npos);
  // Check order
  EXPECT_LT(system_start, system_end);
  EXPECT_LT(system_end, user_start);
  EXPECT_LT(user_start, user_end);
  EXPECT_LT(user_end, assistant_start);
}

TEST_F(PromptBuilderTest, LongMessageHandling) {
  std::string long_message(10000, 'x');
  std::vector<MemoryEntry> empty_memories;
  std::string prompt = builder.build_chat_prompt(
      long_message, empty_memories, PromptBuilder::EPromptFormat::QWEN);
  EXPECT_TRUE(StringUtils::contains(prompt, long_message));
}

} // namespace solus::test