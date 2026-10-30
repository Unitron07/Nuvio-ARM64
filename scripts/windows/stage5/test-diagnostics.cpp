#include "../../../composeApp/src/desktopMain/native/windows/playback_diagnostics.h"
#include <cassert>
#include <limits>
#include <map>
#include <iostream>
int main() {
    using namespace nuvio_diagnostics;
    assert(hardware("d3d11va") == "d3d11va");
    assert(!isHardware("auto") && !isHardware("no") && !isHardware("") && !isHardware("future-decoder"));
    assert(isHardware("d3d11va-copy"));
    for (auto secret : {"https://private/stream?token=secret", "Bearer secret", "h264\nsecret", "\"secret\""}) {
        assert(codec(secret) == "other" && hardware(secret) == "other" && pixel(secret) == "other");
    }
    assert(number(std::numeric_limits<double>::quiet_NaN()) == "null");
    assert(number(std::numeric_limits<double>::infinity()) == "null");
    std::map<std::string, std::string> strings{{"hwdec", "auto"}, {"hwdec-current", "d3d11va"},
        {"track-list/1/type", "video"}, {"track-list/1/codec", "h264"}, {"track-list/1/decoder", "h264"}};
    auto text = [&](const char *key) { return strings[key]; };
    auto numeric = [](const char *key) {
        std::string name(key);
        if (name == "track-list/count") return 2.0;
        if (name == "track-list/1/selected") return 1.0;
        return std::numeric_limits<double>::quiet_NaN();
    };
    auto sample = snapshot(text, numeric);
    assert(sample.find("\"status\":\"hardware-reported\"") != std::string::npos);
    assert(sample.find("\"codec\":\"h264\"") != std::string::npos);
    assert(sample.find("\"avsync\":null") != std::string::npos);
    strings["hwdec-current"] = "no";
    sample = snapshot(text, numeric);
    assert(sample.find("\"status\":\"software\"") != std::string::npos);
    assert(sample.find("\"fallbackReason\":\"not-exposed\"") != std::string::npos);
    strings["hwdec-current"] = "";
    assert(snapshot(text, numeric).find("\"status\":\"unknown\"") != std::string::npos);
    strings["track-list/1/codec"] = "https://secret";
    assert(snapshot(text, numeric).find("secret") == std::string::npos);
    std::cout << "PASS: decoder categories, unavailable properties, active track, software and privacy\n";
}
