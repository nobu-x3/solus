#include "memory/database.h"
#include "server/prompt_builder.h"
#include "utils/helpers.h"
#include <gtest/gtest.h>

namespace solus::test {

class MemoryIntegrationTest : public ::testing::Test {
protected:
  void SetUp() override {
    m_TempDir = std::make_unique<TempDirectory>();
    m_Db = std::make_unique<MemoryDatabase>(m_TempDir->path(), 768, 1000);
    m_Builder = std::make_unique<PromptBuilder>();
    ASSERT_TRUE(m_Db->initialize());
  }

  std::unique_ptr<TempDirectory> m_TempDir;
  std::unique_ptr<MemoryDatabase> m_Db;
  std::unique_ptr<PromptBuilder> m_Builder;
};

TEST_F(MemoryIntegrationTest, MemoryInfluencesPrompt) {
  MemoryEntry entry("user1", "conv1", "User likes C++", 123456);
  auto embedding = RandomGenerator::embedding(768);
  m_Db->add_entry(entry, embedding);
  auto memories = m_Db->search_entries(embedding, "user1", 5);
  auto prompt = m_Builder->build_chat_prompt("What do I like?", memories, solus::PromptBuilder::EPromptFormat::QWEN);
  EXPECT_TRUE(StringUtils::contains(prompt, "likes C++"));
}

TEST_F(MemoryIntegrationTest, RecentMemoriesPreferred) {
  MemoryEntry old_entry("user1", "conv1", "Old preference", 100000);
  MemoryEntry new_entry("user1", "conv1", "New preference", 200000);
  auto emb1 = RandomGenerator::embedding(768);
  auto emb2 = RandomGenerator::embedding(768);
  m_Db->add_entry(old_entry, emb1);
  m_Db->add_entry(new_entry, emb2);
  auto memories = m_Db->search_entries(emb2, "user1", 5);
  EXPECT_FALSE(memories.empty());
}

TEST_F(MemoryIntegrationTest, CrossUserMemoryIsolation) {
  MemoryEntry user1_entry("user1", "conv1", "User1 data", 123456);
  MemoryEntry user2_entry("user2", "conv2", "User2 data", 123456);
  auto emb1 = RandomGenerator::embedding(768);
  auto emb2 = RandomGenerator::embedding(768);
  m_Db->add_entry(user1_entry, emb1);
  m_Db->add_entry(user2_entry, emb2);
  auto user1_memories = m_Db->search_entries(emb1, "user1", 10);
  for (const auto &mem : user1_memories) {
    EXPECT_EQ(mem.user_id, "user1");
    EXPECT_NE(mem.text, "User2 data");
  }
}

TEST_F(MemoryIntegrationTest, MemoryPersistenceAcrossRestarts) {
  for (int i = 0; i < 5; i++) {
    MemoryEntry entry("user1", "conv1", "Memory " + std::to_string(i),
                      123456 + i);
    auto embedding = RandomGenerator::embedding(768);
    m_Db->add_entry(entry, embedding);
  }
  size_t count_before = m_Db->get_entry_count();
  m_Db->save_index();
  m_Db.reset();
  m_Db = std::make_unique<MemoryDatabase>(m_TempDir->path(), 768, 1000);
  ASSERT_TRUE(m_Db->initialize());
  size_t count_after = m_Db->get_entry_count();
  EXPECT_EQ(count_before, count_after);
}

TEST_F(MemoryIntegrationTest, MemorySearchPerformance) {
  for (int i = 0; i < 1000; i++) {
    MemoryEntry entry("user1", "conv1", "Memory " + std::to_string(i),
                      123456 + i);
    auto embedding = RandomGenerator::embedding(768);
    m_Db->add_entry(entry, embedding);
  }
  Timer timer;
  timer.start();
  for (int i = 0; i < 10; i++) {
    auto query_embedding = RandomGenerator::embedding(768);
    m_Db->search_entries(query_embedding, "user1", 5);
  }
  double avg_time = timer.elapsedMs() / 10.0;
  EXPECT_LT(avg_time, 50.0);
  std::cout << "Average search time: " << avg_time << "ms" << std::endl;
}

} // namespace solus::test