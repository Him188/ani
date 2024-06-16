#include "anitorrent.h"

#include <fstream>
#include <iostream>

#include "libtorrent/alert_types.hpp"
#include "libtorrent/bencode.hpp"
#include "libtorrent/magnet_uri.hpp"
#include "libtorrent/session.hpp"


// #include "libtorrent/session.hpp"

namespace anilt::session {
    libtorrent::session *new_session(const char *user_agent) {
        using libtorrent::settings_pack;

        settings_pack settings;
        settings.set_str(settings_pack::user_agent, user_agent);
        settings.set_int(settings_pack::alert_mask,
                         libtorrent::alert_category::status | libtorrent::alert_category::error |
                         libtorrent::alert_category::piece_progress |
                         libtorrent::alert_category::upload |
                         libtorrent::alert_category::stats);

        return new libtorrent::session(settings);
    }

    std::string fetch_magnet(libtorrent::session *s, const std::string &uri, int timeout_seconds,
                             const std::string &save_path) {
        const std::string &magnet_uri = uri;

        lt::session &session = *s;

        lt::add_torrent_params params = lt::parse_magnet_uri(magnet_uri);
        params.save_path = save_path; // specify the save path

        // Check if the torrent is already in the session
        lt::sha1_hash info_hash = params.info_hashes.v1; // Assuming v1, adjust if using v2
        lt::torrent_handle existing_handle = session.find_torrent(info_hash);

        if (existing_handle.is_valid()) {
            std::cout << "Torrent already added." << std::endl;

            return "";
        }

        const auto start_time = std::chrono::high_resolution_clock::now();

        lt::torrent_handle handle = session.add_torrent(params);

        while (true) {
            std::vector<lt::alert *> alerts;
            session.pop_alerts(&alerts);

            for (lt::alert *alert: alerts) {
                std::cout << alert->message() << std::endl;

                if (auto at = lt::alert_cast<lt::add_torrent_alert>(alert)) {
                    std::cout << "Torrent added: " << at->params.ti->name() << std::endl;
                } else if (auto md = lt::alert_cast<lt::metadata_received_alert>(alert)) {
                    std::shared_ptr<const lt::torrent_info> ti = md->handle.torrent_file();

                    // Save the .torrent file
                    std::vector<char> torrent_data;
                    lt::bencode(std::back_inserter(torrent_data), ti->info_section());

                    std::cout << "Metadata received, torrent data is ready." << std::endl;
                    return std::string(torrent_data);
                } else if (auto ec = lt::alert_cast<lt::torrent_error_alert>(alert)) {
                    std::cerr << "Error: " << ec->error.message() << std::endl;
                    return "";
                }
            }


            const auto current_time = std::chrono::high_resolution_clock::now();
            if (std::chrono::duration_cast<std::chrono::seconds>(
                    current_time - start_time).count() >=
                timeout_seconds) {
                std::cerr << "Timeout reached." << std::endl;
                return "";
            }

            std::this_thread::sleep_for(std::chrono::milliseconds(200));
        }
    }
} // namespace anilt::session
