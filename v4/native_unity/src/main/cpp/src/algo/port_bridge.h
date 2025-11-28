#ifndef VITALS_PORT_BRIDGE_H
#define VITALS_PORT_BRIDGE_H

#include <vector>
#include <cstdint>
#include <string>

namespace vitals {

// Call Java Port.encryptImpl from native C++
std::vector<uint8_t> callJavaPortEncrypt(const std::vector<uint8_t> &input, const std::string &key);

// Call Java Port.decryptImpl from native C++
std::vector<uint8_t> callJavaPortDecrypt(const std::vector<uint8_t> &input, const std::string &key);

} // namespace vitals

#endif // VITALS_PORT_BRIDGE_H

