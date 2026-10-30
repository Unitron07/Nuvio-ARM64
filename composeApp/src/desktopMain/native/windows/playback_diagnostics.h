#pragma once

// Only fixed categories and finite numbers cross this diagnostic boundary.
// Never pass filenames, metadata, mpv log messages or header values here.
#include <cmath>
#include <initializer_list>
#include <locale>
#include <sstream>
#include <string>

namespace nuvio_diagnostics {
inline std::string category(const std::string &value, std::initializer_list<const char *> allowed) {
    if (value.empty()) return "unavailable";
    for (auto item : allowed) if (value == item) return value;
    return "other";
}
inline std::string hardware(const std::string &value) {
    return category(value, {"no", "auto", "d3d11va", "d3d11va-copy", "dxva2", "dxva2-copy",
        "nvdec", "nvdec-copy", "vulkan", "vulkan-copy"});
}
inline std::string codec(const std::string &value) {
    return category(value, {"h264", "hevc", "av1", "vp9", "vp8", "mpeg4", "mpeg2video", "rawvideo"});
}
inline std::string decoder(const std::string &value) {
    return category(value, {"h264", "hevc", "av1", "libdav1d", "libaom-av1", "vp9", "libvpx-vp9",
        "vp8", "mpeg4", "mpeg2video", "rawvideo", "h264_d3d11va", "hevc_d3d11va", "av1_d3d11va"});
}
inline std::string pixel(const std::string &value) {
    return category(value, {"d3d11", "d3d11va_vld", "dxva2_vld", "vulkan", "nv12", "p010", "p010le",
        "yuv420p", "yuv420p10le", "yuv422p", "yuv444p", "bgra", "bgr0", "rgb24"});
}
inline std::string number(double value) {
    if (!std::isfinite(value)) return "null";
    std::ostringstream out;
    out.imbue(std::locale::classic());
    out.precision(12);
    out << value;
    return out.str();
}
inline bool isHardware(const std::string &value) {
    auto safe = hardware(value);
    return safe != "no" && safe != "auto" && safe != "other" && safe != "unavailable";
}

// Query functions return empty/NaN on unavailable properties, never zero defaults.
// Used by the actual player and deterministic tests with an injected property source.
template<class StringQuery, class NumberQuery>
std::string snapshot(StringQuery text, NumberQuery numeric) {
    auto requested = hardware(text("hwdec"));
    auto current = hardware(text("hwdec-current"));
    std::string videoCodec, videoDecoder;
    double count = numeric("track-list/count");
    for (int i = 0; i < 128 && i < count; ++i) {
        std::string prefix = "track-list/" + std::to_string(i) + "/";
        if (text((prefix + "type").c_str()) == "video" && numeric((prefix + "selected").c_str()) == 1) {
            videoCodec = text((prefix + "codec").c_str());
            videoDecoder = text((prefix + "decoder").c_str());
            break;
        }
    }
    std::ostringstream out;
    out << "\"requested\":\"" << requested << "\",\"current\":\"" << current
        << "\",\"status\":\"" << (isHardware(current) ? "hardware-reported" : current == "no" ? "software" : "unknown")
        << "\",\"fallbackReason\":\"" << (current == "no" && requested != "no" ? "not-exposed" : "not-established")
        << "\",\"codec\":\"" << codec(videoCodec) << "\",\"decoder\":\"" << decoder(videoDecoder)
        << "\",\"pixelFormat\":\"" << pixel(text("video-params/pixelformat")) << "\"";
    for (auto field : {"video-params/w", "video-params/h", "container-fps", "estimated-vf-fps",
            "time-pos", "pause", "core-idle", "frame-drop-count", "decoder-frame-drop-count",
            "avsync", "demuxer-cache-duration"}) {
        out << ",\"" << field << "\":" << number(numeric(field));
    }
    return out.str();
}
} // namespace nuvio_diagnostics
