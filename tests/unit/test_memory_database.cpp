#include <memory/database.h>
#include "utils/helpers.h"
#include <gtest/gtest.h>

namespace solus::test {

class MemoryDatabaseTest : public ::testing::Test {
protected:
    void SetUp() override {
        temp_dir = std::make_unique<TempDirectory>();
        db = std::make_unique<MemoryDatabase>(temp_dir->path(), 768, 1000);
        ASSERT_TRUE(db->initialize());
    }    
    std::unique_ptr<TempDirectory> temp_dir;
    std::unique_ptr<MemoryDatabase> db;
};

TEST_F(MemoryDatabaseTest, Initialize) {
    EXPECT_EQ(db->get_entry_count(), 0);
}

TEST_F(MemoryDatabaseTest, AddSingleMemory) {
    MemoryEntry entry("user1", "conv1", "Test memory", 123456);
    auto embedding = RandomGenerator::embedding(768);
    db->add_entry(entry, embedding);
    EXPECT_EQ(db->get_entry_count(), 1);
}

TEST_F(MemoryDatabaseTest, AddMultipleMemories) {
    for (int i = 0; i < 10; i++) {
        MemoryEntry entry(
            "user1",
            "conv1",
            "Memory " + std::to_string(i),
            123456 + i
        );
        auto embedding = RandomGenerator::embedding(768);
        db->add_entry(entry, embedding);
    }
    EXPECT_EQ(db->get_entry_count(), 10);
}

TEST_F(MemoryDatabaseTest, search_entries) {
    // Add some memories
    for (int i = 0; i < 5; i++) {
        MemoryEntry entry(
            "user1",
            "conv1",
            "Memory " + std::to_string(i),
            123456 + i
        );
        auto embedding = RandomGenerator::embedding(768);
        db->add_entry(entry, embedding);
    }
    // Search
    auto query_embedding = RandomGenerator::embedding(768);
    auto results = db->search_entries(query_embedding, "user1", 3);
    EXPECT_LE(results.size(), 3);
}

TEST_F(MemoryDatabaseTest, SearchByUserId) {
    // Add memories for different users
    MemoryEntry entry1("user1", "conv1", "User1 memory", 123456);
    MemoryEntry entry2("user2", "conv2", "User2 memory", 123457);
    auto emb1 = RandomGenerator::embedding(768);
    auto emb2 = RandomGenerator::embedding(768);
    db->add_entry(entry1, emb1);
    db->add_entry(entry2, emb2);
    // Search for user1
    auto results = db->search_entries(emb1, "user1", 10);
    // Should only return user1's memories
    for (const auto& result : results) {
        EXPECT_EQ(result.user_id, "user1");
    }
}

TEST_F(MemoryDatabaseTest, SearchEmptyDatabase) {
    auto query_embedding = RandomGenerator::embedding(768);
    auto results = db->search_entries(query_embedding, "user1", 5);
    EXPECT_TRUE(results.empty());
}

TEST_F(MemoryDatabaseTest, SaveAndLoad) {
    // Add memories
    for (int i = 0; i < 5; i++) {
        MemoryEntry entry(
            "user1",
            "conv1",
            "Memory " + std::to_string(i),
            123456 + i
        );
        auto embedding = RandomGenerator::embedding(768);
        db->add_entry(entry, embedding);
    }
    size_t original_count = db->get_entry_count();
    // Save
    db->save_index();
    // Create new database instance
    auto new_db = std::make_unique<MemoryDatabase>(temp_dir->path(), 768, 1000);
    ASSERT_TRUE(new_db->initialize());
    // Should load the saved data
    EXPECT_EQ(new_db->get_entry_count(), original_count);
}

TEST_F(MemoryDatabaseTest, WrongEmbeddingDimension) {
    MemoryEntry entry("user1", "conv1", "Test", 123456);
    auto wrong_embedding = RandomGenerator::embedding(512); // Wrong size
    // Should not crash, but might not add
    db->add_entry(entry, wrong_embedding);
    // Implementation should handle this gracefully
}

TEST_F(MemoryDatabaseTest, LargeScaleMemories) {
    Timer timer;
    timer.start();
    // Add 1000 memories
    for (int i = 0; i < 1000; i++) {
        MemoryEntry entry(
            "user" + std::to_string(i % 10),
            "conv1",
            "Memory " + std::to_string(i),
            123456 + i
        );
        auto embedding = RandomGenerator::embedding(768);
        db->add_entry(entry, embedding);
    }
    double add_time = timer.elapsedMs();
    // Search should be fast
    timer.start();
    auto query_embedding = RandomGenerator::embedding(768);
    auto results = db->search_entries(query_embedding, "user5", 10);
    double search_time = timer.elapsedMs();
    EXPECT_EQ(db->get_entry_count(), 1000);
    EXPECT_LT(search_time, 100.0); // Search should be < 100ms
    std::cout << "Add time for 1000 memories: " << add_time << "ms" << std::endl;
    std::cout << "Search time: " << search_time << "ms" << std::endl;
}

} // namespace solus::test