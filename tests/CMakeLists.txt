cmake_minimum_required(VERSION 3.18)

# Fetch Google Test
include(FetchContent)
FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG release-1.12.1
)

# For Windows: Prevent overriding the parent project's compiler/linker settings
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

enable_testing()

# Helper function to add tests
function(add_solus_test TEST_NAME)
    add_executable(${TEST_NAME} ${ARGN})
    
    target_include_directories(${TEST_NAME} PRIVATE
        ${PROJECT_SOURCE_DIR}/include
        ${PROJECT_SOURCE_DIR}/vendor/json/include
        ${PROJECT_SOURCE_DIR}/vendor/hnswlib
    )
    
    target_link_libraries(${TEST_NAME}
        gtest
        gtest_main
        gmock
    )
    
    add_test(NAME ${TEST_NAME} COMMAND ${TEST_NAME})
    
    # Set timeout for tests
    set_tests_properties(${TEST_NAME} PROPERTIES TIMEOUT 60)
endfunction()

# Unit tests
add_solus_test(test_config
    unit/test_config.cpp
)