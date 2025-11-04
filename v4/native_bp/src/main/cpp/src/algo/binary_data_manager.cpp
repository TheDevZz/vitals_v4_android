#include "binary_data_manager.hpp"
#include <iostream>

namespace vitals {

  void BinaryDataManager::storeData(const std::string &tag, const uint8_t *data, size_t size) {
    std::lock_guard<std::mutex> lock(mutex_);
    dataMap_[tag] = std::vector<uint8_t>(data, data + size);
  }

  bool BinaryDataManager::getData(const std::string &tag, std::vector<uint8_t> &outData) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = dataMap_.find(tag);
    if (it != dataMap_.end()) {
      outData = it->second;
      return true;
    }
    return false;
  }

  bool BinaryDataManager::removeData(const std::string &tag) {
    std::lock_guard<std::mutex> lock(mutex_);
    return dataMap_.erase(tag) > 0;
  }

  bool BinaryDataManager::hasData(const std::string &tag) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return dataMap_.find(tag) != dataMap_.end();
  }

  std::vector<std::string> BinaryDataManager::getAllTags() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<std::string> tags;
    tags.reserve(dataMap_.size());

    for (const auto &pair: dataMap_) {
      tags.push_back(pair.first);
    }

    return tags;
  }

} // namespace vitals