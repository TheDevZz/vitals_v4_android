#ifndef VITALS_BINARY_DATA_MANAGER_HPP
#define VITALS_BINARY_DATA_MANAGER_HPP

#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include <iostream>

namespace vitals {

  class BinaryDataManager {
  public:
    static BinaryDataManager &getInstance() {
      static BinaryDataManager instance;
      return instance;
    }

    // 存储二进制数据
    void storeData(const std::string &tag, const uint8_t *data, size_t size);

    // 获取二进制数据
    bool getData(const std::string &tag, std::vector<uint8_t> &outData) const;

    // 删除二进制数据
    bool removeData(const std::string &tag);

    // 检查tag是否存在
    bool hasData(const std::string &tag) const;

    // 获取所有tag
    std::vector<std::string> getAllTags() const;

  private:
    BinaryDataManager() = default;

    ~BinaryDataManager() = default;

    // 禁止拷贝和赋值
    BinaryDataManager(const BinaryDataManager &) = delete;

    BinaryDataManager &operator=(const BinaryDataManager &) = delete;

    mutable std::mutex mutex_;
    std::unordered_map<std::string, std::vector<uint8_t>> dataMap_;
  };

} // namespace vitals

#endif // VITALS_BINARY_DATA_MANAGER_HPP