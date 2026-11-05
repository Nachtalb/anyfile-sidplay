// SPDX-License-Identifier: GPL-2.0-or-later
// Host harness: sidrender <tune.sid> <out.wav> [subsong] [seconds] [rate]
// Builds with the same CMakeLists as the app (non-Android branch).
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iterator>
#include <vector>

#include "sid_core.h"

static void le(std::ofstream& f, uint32_t v, int n) {
    for (int i = 0; i < n; i++) f.put(static_cast<char>((v >> (8 * i)) & 0xff));
}

int main(int argc, char** argv) {
    if (argc < 3) { std::fprintf(stderr, "usage: %s tune.sid out.wav [subsong] [seconds] [rate]\n", argv[0]); return 2; }
    std::ifstream in(argv[1], std::ios::binary);
    std::vector<uint8_t> data((std::istreambuf_iterator<char>(in)), {});
    unsigned sub = argc > 3 ? std::atoi(argv[3]) : 0;
    unsigned secs = argc > 4 ? std::atoi(argv[4]) : 10;
    const unsigned rate = argc > 5 ? std::atoi(argv[5]) : 48000;

    SidTuneMeta meta; std::string err;
    if (!sid_probe(data.data(), data.size(), meta, err)) { std::fprintf(stderr, "probe: %s\n", err.c_str()); return 1; }
    std::printf("format=%s songs=%u start=%u roms=%d\n", meta.format.c_str(), meta.songs, meta.startSong, meta.needsRoms);
    for (auto& s : meta.info) std::printf("  %s\n", s.c_str());

    SidSession* s = sid_open(data.data(), data.size(), sub, rate, err);
    if (!s) { std::fprintf(stderr, "open: %s\n", err.c_str()); return 1; }
    unsigned ch = sid_channels(s);
    std::vector<int16_t> pcm(static_cast<size_t>(rate) * secs * ch);
    int frames = sid_render(s, pcm.data(), rate * secs);
    sid_close(s);
    if (frames < 0) { std::fprintf(stderr, "render failed\n"); return 1; }

    uint32_t bytes = static_cast<uint32_t>(frames) * ch * 2;
    std::ofstream out(argv[2], std::ios::binary);
    out.write("RIFF", 4); le(out, 36 + bytes, 4); out.write("WAVEfmt ", 8);
    le(out, 16, 4); le(out, 1, 2); le(out, ch, 2); le(out, rate, 4); le(out, rate * ch * 2, 4); le(out, ch * 2, 2); le(out, 16, 2);
    out.write("data", 4); le(out, bytes, 4);
    out.write(reinterpret_cast<const char*>(pcm.data()), bytes);
    std::printf("frames=%d channels=%u\n", frames, ch);
    return 0;
}
