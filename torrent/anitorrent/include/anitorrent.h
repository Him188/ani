#ifndef ANILT_H
#define ANILT_H

#include "libtorrent/session.hpp"

extern "C" {
namespace anilt::session {
    struct stats {
    };

    libtorrent::session *new_session(const char *user_agent);

    // stats get_stats(libtorrent::session &s) {}
    std::string fetch_magnet(libtorrent::session *s, const std::string &uri, int timeout_seconds,
                             const std::string &save_path);
} // namespace anilt::session
}

#endif // ANILT_H
