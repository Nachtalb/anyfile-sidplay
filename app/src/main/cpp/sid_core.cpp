// SPDX-License-Identifier: GPL-2.0-or-later
#include "sid_core.h"

#include <algorithm>
#include <cstring>
#include <memory>

#include "sidplayfp/sidplayfp.h"
#include "sidplayfp/SidConfig.h"
#include "sidplayfp/SidTune.h"
#include "sidplayfp/SidTuneInfo.h"
#include "builders/residfp-builder/residfp.h"

namespace {
constexpr unsigned CYCLES_PER_STEP = 20000;  // ~20 ms of C64 time per emulation step
}

struct SidSession {
    std::unique_ptr<SidTune> tune;
    std::unique_ptr<ReSIDfpBuilder> builder;
    sidplayfp engine;
    unsigned channels = 1;
    std::vector<int16_t> buf;   // mixed samples not yet handed out
    size_t pos = 0;
};

static bool needsRoms(const SidTuneInfo* info) {
    auto c = info->compatibility();
    return c == SidTuneInfo::COMPATIBILITY_R64 || c == SidTuneInfo::COMPATIBILITY_BASIC;
}

bool sid_probe(const uint8_t* data, size_t len, SidTuneMeta& meta, std::string& err) {
    SidTune tune(data, static_cast<uint_least32_t>(len));
    if (!tune.getStatus()) {
        err = tune.statusString();
        return false;
    }
    const SidTuneInfo* info = tune.getInfo();
    meta.songs = info->songs();
    meta.startSong = info->startSong();
    meta.info.clear();
    for (unsigned i = 0; i < info->numberOfInfoStrings(); i++) meta.info.emplace_back(info->infoString(i));
    meta.format = info->formatString() ? info->formatString() : "";
    meta.needsRoms = needsRoms(info);
    return true;
}

SidSession* sid_open(const uint8_t* data, size_t len, unsigned subsong, unsigned rate, std::string& err) {
    auto s = std::make_unique<SidSession>();
    s->tune = std::make_unique<SidTune>(data, static_cast<uint_least32_t>(len));
    if (!s->tune->getStatus()) {
        err = s->tune->statusString();
        return nullptr;
    }
    s->tune->selectSong(subsong);

    s->builder = std::make_unique<ReSIDfpBuilder>("anyfile-sidplay");

    SidConfig cfg;
    cfg.frequency = rate;
    cfg.samplingMethod = SidConfig::INTERPOLATE;
    cfg.sidEmulation = s->builder.get();
    if (!s->engine.config(cfg)) {
        err = s->engine.error();
        return nullptr;
    }
    if (!s->engine.load(s->tune.get())) {
        err = s->engine.error();
        return nullptr;
    }
    s->channels = s->engine.installedSIDs() > 1 ? 2 : 1;
    s->engine.initMixer(s->channels == 2);
    return s.release();
}

unsigned sid_channels(const SidSession* s) { return s->channels; }

int sid_render(SidSession* s, int16_t* out, unsigned maxFrames) {
    const size_t want = static_cast<size_t>(maxFrames) * s->channels;
    size_t done = 0;
    std::vector<int16_t> step;
    while (done < want) {
        if (s->pos >= s->buf.size()) {
            int samples = s->engine.play(CYCLES_PER_STEP);
            if (samples < 0) return -1;
            if (samples == 0) break;
            step.resize(static_cast<size_t>(samples) * s->channels + 16);
            unsigned mixed = s->engine.mix(step.data(), static_cast<unsigned>(samples));
            s->buf.assign(step.begin(), step.begin() + mixed);
            s->pos = 0;
            if (mixed == 0) continue;
        }
        size_t n = std::min(want - done, s->buf.size() - s->pos);
        std::memcpy(out + done, s->buf.data() + s->pos, n * sizeof(int16_t));
        done += n;
        s->pos += n;
    }
    return static_cast<int>(done / s->channels);
}

void sid_close(SidSession* s) { delete s; }
