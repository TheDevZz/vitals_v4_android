#include "inspect.h"

Inspector* inspector_instance;
Inspector* fake_inspector = new Inspector();

void setup_inspector(Inspector* inspector) {
  if (inspector_instance != inspector) {
    remove_inspector();
  }
  inspector_instance = inspector;
}

Inspector* get_inspector() {
  if (inspector_instance != nullptr) {
    return inspector_instance;
  } else {
    return fake_inspector;
  }
}

void remove_inspector() {
  if (inspector_instance != nullptr) {
    delete inspector_instance;
    inspector_instance = nullptr;
  }
}
