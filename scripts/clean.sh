#!/bin/bash
cd "$(dirname "$0")/.."
rm -rf build 
rm -rf memory_db
echo "Cleaned build artifacts and memory database"