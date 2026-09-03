import SwiftUI

public enum ServiceType: String, CaseIterable, Identifiable, Codable, Hashable, Sendable {
    case portainer
    case pihole
    case adguardHome
    case technitium
    case beszel
    case healthchecks
    case linuxUpdate = "linux_update"
    case dockhand
    case dockmon
    case komodo
    case maltrail
    case uptimeKuma = "uptime_kuma"
    case craftyController = "crafty_controller"
    case unifiNetwork = "unifi_network"
    case gitea
    case nginxProxyManager
    case pangolin
    case patchmon
    case jellystat
    case plex
    case radarr
    case sonarr
    case lidarr
    case qbittorrent
    case jellyseerr
    case prowlarr
    case bazarr
    case gluetun
    case flaresolverr
    case wakapi
    case proxmox
    case proxmoxBackupServer = "proxmox_backup_server"
    case prometheus
    case grafana
    case netbox
    case zammad
    case pegaprox
    case opnsense
    case oneuptime
    case truenas
    case pterodactyl
    case calagopus

    public var id: String { rawValue }

    public static func fromStoredRawValue(_ rawValue: String) -> ServiceType? {
        if let direct = ServiceType(rawValue: rawValue) {
            return direct
        }

        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        if let caseInsensitive = ServiceType.allCases.first(where: {
            $0.rawValue.caseInsensitiveCompare(trimmed) == .orderedSame
        }) {
            return caseInsensitive
        }

        let normalized = trimmed
            .replacingOccurrences(of: "-", with: "_")
            .lowercased()

        switch normalized {
        case "linuxupdate", "linux_update":
            return .linuxUpdate
        case "technitium", "technitium_dns", "technitiumdns":
            return .technitium
        case "dockhand":
            return .dockhand
        case "dockmon":
            return .dockmon
        case "komodo":
            return .komodo
        case "maltrail":
            return .maltrail
        case "uptimekuma", "uptime_kuma":
            return .uptimeKuma
        case "pangolin":
            return .pangolin
        case "crafty", "crafty_controller":
            return .craftyController
        case "unifi", "ubiquiti", "unifi_network", "unifinetwork":
            return .unifiNetwork
        case "proxmox", "proxmox_ve", "proxmoxve", "pve":
            return .proxmox
        case "pbs", "proxmox_backup", "proxmox_backup_server", "proxmoxbackupserver":
            return .proxmoxBackupServer
        case "prometheus":
            return .prometheus
        case "grafana":
            return .grafana
        case "netbox":
            return .netbox
        case "zammad":
            return .zammad
        case "pegaprox", "pega_prox":
            return .pegaprox
        case "opnsense", "opn_sense":
            return .opnsense
        case "oneuptime", "one_uptime":
            return .oneuptime
        case "truenas", "truenas_scale", "truenasscale", "truenas_core", "truenascore":
            return .truenas
        case "pterodactyl":
            return .pterodactyl
        case "calagopus":
            return .calagopus
        default:
            return nil
        }
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let rawValue = try container.decode(String.self)
        guard let mapped = ServiceType.fromStoredRawValue(rawValue) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unknown ServiceType raw value: \(rawValue)"
            )
        }
        self = mapped
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    public static let mediaServices: [ServiceType] = [
        .radarr,
        .sonarr,
        .lidarr,
        .qbittorrent,
        .jellyseerr,
        .prowlarr,
        .bazarr,
        .gluetun,
        .flaresolverr
    ]

    public static var homeServices: [ServiceType] {
        allCases.filter { !mediaServices.contains($0) }
    }

    public var isMediaService: Bool {
        Self.mediaServices.contains(self)
    }

    public var displayName: String {
        switch self {
        case .portainer:          return "Portainer"
        case .pihole:             return "Pi-hole"
        case .adguardHome:        return "AdGuard Home"
        case .technitium:         return "Technitium DNS"
        case .beszel:             return "Beszel"
        case .healthchecks:       return "Healthchecks"
        case .linuxUpdate:             return "Linux Update"
        case .dockhand:                return "Dockhand"
        case .dockmon:                 return "DockMon"
        case .komodo:                  return "Komodo"
        case .maltrail:                return "Maltrail"
        case .uptimeKuma:              return "Uptime Kuma"
        case .craftyController:        return "Crafty Controller"
        case .unifiNetwork:            return "Ubiquiti Network"
        case .gitea:              return "Gitea"
        case .nginxProxyManager:  return "Nginx Proxy Manager"
        case .pangolin:           return "Pangolin"
        case .patchmon:           return "PatchMon"
        case .jellystat:          return "Jellystat"
        case .plex:               return "Plex"
        case .radarr:             return "Radarr"
        case .sonarr:             return "Sonarr"
        case .lidarr:             return "Lidarr"
        case .qbittorrent:        return "qBittorrent"
        case .jellyseerr:         return "Jellyseerr"
        case .prowlarr:           return "Prowlarr"
        case .bazarr:             return "Bazarr"
        case .gluetun:            return "Gluetun"
        case .flaresolverr:       return "FlareSolverr"
        case .wakapi:             return "Wakapi"
        case .proxmox:            return "Proxmox VE"
        case .proxmoxBackupServer: return "Proxmox Backup Server"
        case .prometheus:         return "Prometheus"
        case .grafana:            return "Grafana"
        case .netbox:             return "NetBox"
        case .zammad:             return "Zammad"
        case .pegaprox:           return "PegaProx"
        case .opnsense:           return "OPNsense"
        case .oneuptime:          return "OneUptime"
        case .truenas:            return "TrueNAS"
        case .pterodactyl:        return "Pterodactyl"
        case .calagopus:          return "Calagopus"
        }
    }

    func localizedDescription(using t: Translations) -> String {
        switch self {
        case .portainer:          return t.servicePortainerDesc
        case .pihole:             return t.servicePiholeDesc
        case .adguardHome:        return t.serviceAdguardDesc
        case .technitium:         return t.serviceTechnitiumDesc
        case .beszel:             return t.serviceBeszelDesc
        case .healthchecks:       return t.serviceHealthchecksDesc
        case .linuxUpdate:             return t.serviceLinuxUpdateDesc
        case .dockhand:                return t.serviceDockhandDesc
        case .dockmon:                 return t.serviceDockmonDesc
        case .komodo:                  return t.serviceKomodoDesc
        case .maltrail:                return t.serviceMaltrailDesc
        case .uptimeKuma:              return t.serviceUptimeKumaDesc
        case .craftyController:        return t.serviceCraftyControllerDesc
        case .unifiNetwork:            return t.serviceUnifiNetworkDesc
        case .gitea:              return t.serviceGiteaDesc
        case .nginxProxyManager:  return t.serviceNpmDesc
        case .pangolin:           return t.servicePangolinDesc
        case .patchmon:           return t.servicePatchmonDesc
        case .jellystat:          return t.serviceJellystatDesc
        case .plex:               return t.servicePlexDesc
        case .radarr:             return t.serviceRadarrDesc
        case .sonarr:             return t.serviceSonarrDesc
        case .lidarr:             return t.serviceLidarrDesc
        case .qbittorrent:        return t.serviceQbittorrentDesc
        case .jellyseerr:         return t.serviceJellyseerrDesc
        case .prowlarr:           return t.serviceProwlarrDesc
        case .bazarr:             return t.serviceBazarrDesc
        case .gluetun:            return t.serviceGluetunDesc
        case .flaresolverr:       return t.serviceFlaresolverrDesc
        case .wakapi:             return t.serviceWakapiDesc
        case .proxmox:            return t.serviceProxmoxDesc
        case .proxmoxBackupServer: return "Read-only datastore capacity and maintenance monitoring"
        case .prometheus:         return "Read-only scrape target and active alert monitoring"
        case .grafana:            return "Read-only dashboard and data source inventory"
        case .netbox:             return "Read-only device and virtual machine inventory"
        case .zammad:             return "Read-only, PII-redacted ticket operations view"
        case .pegaprox:           return "Tenant-scoped cluster, guest and active alert monitoring"
        case .opnsense:           return "Read-only firewall health and interface inventory"
        case .oneuptime:          return "Read-only monitor, alert and incident operations view"
        case .truenas:            return t.serviceTruenasDesc
        case .pterodactyl:        return t.servicePterodactylDesc
        case .calagopus:          return t.serviceCalagopusDesc
        }
    }

    @MainActor
    public var description: String {
        localizedDescription(using: Translations.current())
    }

    public var symbolName: String {
        switch self {
        case .portainer:          return "shippingbox.fill"
        case .pihole:             return "shield.fill"
        case .adguardHome:        return "shield.lefthalf.filled"
        case .technitium:         return "network.badge.shield.half.filled"
        case .beszel:             return "server.rack"
        case .healthchecks:       return "heart.text.square.fill"
        case .linuxUpdate:             return "chevron.left.forwardslash.chevron.right"
        case .dockhand:                return "shippingbox.circle.fill"
        case .dockmon:                 return "arrow.triangle.2.circlepath.circle.fill"
        case .komodo:                  return "shippingbox.fill"
        case .maltrail:                return "network.badge.shield.half.filled"
        case .uptimeKuma:              return "heart.text.square.fill"
        case .craftyController:        return "gamecontroller.fill"
        case .unifiNetwork:            return "dot.radiowaves.left.and.right"
        case .gitea:              return "arrow.triangle.branch"
        case .nginxProxyManager:  return "globe"
        case .pangolin:           return "point.3.connected.trianglepath.dotted"
        case .patchmon:           return "shippingbox.circle.fill"
        case .jellystat:          return "chart.line.uptrend.xyaxis"
        case .plex:               return "play.tv"
        case .radarr:             return "film.fill"
        case .sonarr:             return "tv.fill"
        case .lidarr:             return "music.note.list"
        case .qbittorrent:        return "arrow.down.circle.fill"
        case .jellyseerr:         return "star.fill"
        case .prowlarr:           return "magnifyingglass.circle.fill"
        case .bazarr:             return "text.bubble.fill"
        case .gluetun:            return "lock.shield.fill"
        case .flaresolverr:       return "flame.fill"
        case .wakapi:             return "timer"
        case .proxmox:            return "cpu"
        case .proxmoxBackupServer: return "externaldrive.badge.checkmark"
        case .prometheus:         return "waveform.path.ecg"
        case .grafana:            return "chart.xyaxis.line"
        case .netbox:             return "server.rack"
        case .zammad:             return "ticket.fill"
        case .pegaprox:           return "point.3.connected.trianglepath.dotted"
        case .opnsense:           return "shield.lefthalf.filled"
        case .oneuptime:          return "waveform.path.ecg.rectangle"
        case .truenas:            return "externaldrive.connected.to.line.below.fill"
        case .pterodactyl:        return "gamecontroller.fill"
        case .calagopus:          return "bird.fill"
        }
    }

    public var iconUrl: String {
        switch self {
        case .portainer:          return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/portainer.png"
        case .pihole:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pi-hole.png"
        case .adguardHome:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/adguard-home.png"
        case .technitium:         return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/technitium.png"
        case .beszel:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/beszel.png"
        case .healthchecks:       return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/healthchecks.png"
        case .linuxUpdate:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/linux-update-dashboard.png"
        case .dockhand:                return "https://dockhand.pro/favicon.ico"
        case .dockmon:                 return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/dockmon.png"
        case .komodo:                  return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/komodo.png"
        case .maltrail:                return "https://raw.githubusercontent.com/stamparm/maltrail/master/html/images/mlogo.png"
        case .uptimeKuma:              return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/uptime-kuma.png"
        case .craftyController:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/crafty-controller.png"
        case .unifiNetwork:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/ubiquiti-unifi.png"
        case .gitea:              return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gitea.png"
        case .nginxProxyManager:  return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/nginx-proxy-manager.png"
        case .pangolin:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pangolin.png"
        case .patchmon:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/patchmon.png"
        case .jellystat:          return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellystat.png"
        case .plex:               return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/plex.png"
        case .radarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/radarr.png"
        case .sonarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/sonarr.png"
        case .lidarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/lidarr.png"
        case .qbittorrent:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/qbittorrent.png"
        case .jellyseerr:         return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellyseerr.png"
        case .prowlarr:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/prowlarr.png"
        case .bazarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/bazarr.png"
        case .gluetun:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gluetun.png"
        case .flaresolverr:       return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/flaresolverr.png"
        case .wakapi:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/wakapi.png"
        case .proxmox:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/proxmox.png"
        case .proxmoxBackupServer: return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/proxmox-backup-server.png"
        case .prometheus:         return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/prometheus.png"
        case .grafana:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/grafana.png"
        case .netbox:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/netbox.png"
        case .zammad:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/zammad.png"
        case .pegaprox:           return "https://raw.githubusercontent.com/PegaProx/project-pegaprox/main/web/favicon.png"
        case .opnsense:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/opnsense.png"
        case .oneuptime:          return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/oneuptime.png"
        case .truenas:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/truenas-scale.png"
        case .pterodactyl:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pterodactyl.png"
        case .calagopus:          return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/calagopus.png"
        }
    }

    public var iconCandidates: [URL] {
        if self == .truenas {
            return URL(string: "https://cdn.jsdelivr.net/gh/selfhst/icons/png/truenas-scale.png")
                .map { [$0] } ?? []
        }

        let slug: String
        switch self {
        case .portainer:          slug = "portainer"
        case .pihole:             slug = "pi-hole"
        case .adguardHome:        slug = "adguard-home"
        case .technitium:         slug = "technitium"
        case .beszel:             slug = "beszel"
        case .healthchecks:       slug = "healthchecks"
        case .linuxUpdate:             slug = "linux-update"
        case .dockhand:                slug = "dockhand"
        case .dockmon:                 slug = "dockmon"
        case .komodo:                  slug = "komodo"
        case .maltrail:                slug = "maltrail"
        case .uptimeKuma:              slug = "uptime-kuma"
        case .craftyController:        slug = "crafty-controller"
        case .unifiNetwork:            slug = "unifi"
        case .gitea:              slug = "gitea"
        case .nginxProxyManager:  slug = "nginx-proxy-manager"
        case .pangolin:           slug = "pangolin"
        case .patchmon:           slug = "patchmon"
        case .jellystat:          slug = "jellystat"
        case .plex:               slug = "plex"
        case .radarr:             slug = "radarr"
        case .sonarr:             slug = "sonarr"
        case .lidarr:             slug = "lidarr"
        case .qbittorrent:        slug = "qbittorrent"
        case .jellyseerr:         slug = "jellyseerr"
        case .prowlarr:           slug = "prowlarr"
        case .bazarr:             slug = "bazarr"
        case .gluetun:            slug = "gluetun"
        case .flaresolverr:       slug = "flaresolverr"
        case .wakapi:             slug = "wakapi"
        case .proxmox:            slug = "proxmox"
        case .proxmoxBackupServer: slug = "proxmox-backup-server"
        case .prometheus:         slug = "prometheus"
        case .grafana:            slug = "grafana"
        case .netbox:             slug = "netbox"
        case .zammad:             slug = "zammad"
        case .pegaprox:           slug = "pegaprox"
        case .opnsense:           slug = "opnsense"
        case .oneuptime:          slug = "oneuptime"
        case .truenas:            slug = "truenas-scale"
        case .pterodactyl:        slug = "pterodactyl"
        case .calagopus:          slug = "calagopus"
        }
        var orderedCandidates: [String] = []
        let primary = iconUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if !primary.isEmpty {
            orderedCandidates.append(primary)
        }
        orderedCandidates.append("https://cdn.jsdelivr.net/gh/selfhst/icons/png/\(slug).png")
        orderedCandidates.append("https://raw.githubusercontent.com/selfhst/icons/main/png/\(slug).png")
        if self == .technitium {
            orderedCandidates.append("https://cdn.jsdelivr.net/gh/selfhst/icons/png/technitium-dns-server.png")
            orderedCandidates.append("https://raw.githubusercontent.com/selfhst/icons/main/png/technitium-dns-server.png")
        }
        var seen = Set<String>()
        let deduped = orderedCandidates.filter { seen.insert($0).inserted }
        return deduped.compactMap(URL.init(string:))
    }

    public var localIconAssetName: String {
        switch self {
        case .portainer:          return "service-portainer"
        case .pihole:             return "service-pi-hole"
        case .adguardHome:        return "service-adguard-home"
        case .technitium:         return "service-technitium-dns-server"
        case .beszel:             return "service-beszel"
        case .healthchecks:       return "service-healthchecks"
        case .linuxUpdate:             return "service-linux-update"
        case .dockhand:                return "service-dockhand"
        case .dockmon:                 return "service-dockmon"
        case .komodo:                  return "service-komodo"
        case .maltrail:                return "service-maltrail"
        case .uptimeKuma:              return "service-uptime-kuma"
        case .craftyController:        return "service-crafty-controller"
        case .unifiNetwork:            return "service-unifi"
        case .gitea:              return "service-gitea"
        case .nginxProxyManager:  return "service-nginx-proxy-manager"
        case .pangolin:           return "service-pangolin"
        case .patchmon:           return "service-patchmon"
        case .jellystat:          return "service-jellystat"
        case .plex:               return "service-plex"
        case .radarr:             return "service-radarr"
        case .sonarr:             return "service-sonarr"
        case .lidarr:             return "service-lidarr"
        case .qbittorrent:        return "service-qbittorrent"
        case .jellyseerr:         return "service-jellyseerr"
        case .prowlarr:           return "service-prowlarr"
        case .bazarr:             return "service-bazarr"
        case .gluetun:            return "service-gluetun"
        case .flaresolverr:       return "service-flaresolverr"
        case .wakapi:             return "service-wakapi"
        case .proxmox:            return "service-proxmox"
        case .proxmoxBackupServer: return "service-proxmox"
        case .prometheus:         return "service-prometheus"
        case .grafana:            return "service-grafana"
        case .netbox:             return "service-netbox"
        case .zammad:             return "service-zammad"
        case .pegaprox:           return "service-pegaprox"
        case .opnsense:           return "service-opnsense"
        case .oneuptime:          return "service-oneuptime"
        case .truenas:            return "service-truenas"
        case .pterodactyl:        return "service-pterodactyl"
        case .calagopus:          return "service-calagopus"
        }
    }

    public var colors: ServiceColorSet {
        switch self {
        case .portainer:          return ServiceColorSet(primary: Color(hex: "#13B5EA"), dark: Color(hex: "#0D8ECF"), bg: Color(hex: "#13B5EA").opacity(0.09))
        case .pihole:             return ServiceColorSet(primary: Color(hex: "#CD2326"), dark: Color(hex: "#9B1B1E"), bg: Color(hex: "#CD2326").opacity(0.09))
        case .adguardHome:        return ServiceColorSet(primary: Color(hex: "#68BC71"), dark: Color(hex: "#4C9A56"), bg: Color(hex: "#68BC71").opacity(0.09))
        case .technitium:         return ServiceColorSet(primary: Color(hex: "#2D9CDB"), dark: Color(hex: "#1D74A6"), bg: Color(hex: "#2D9CDB").opacity(0.09))
        case .beszel:             return ServiceColorSet(primary: Color(hex: "#8B5CF6"), dark: Color(hex: "#6D28D9"), bg: Color(hex: "#8B5CF6").opacity(0.09))
        case .healthchecks:       return ServiceColorSet(primary: Color(hex: "#16A34A"), dark: Color(hex: "#15803D"), bg: Color(hex: "#16A34A").opacity(0.09))
        case .linuxUpdate:             return ServiceColorSet(primary: Color(hex: "#14B8A6"), dark: Color(hex: "#0F766E"), bg: Color(hex: "#14B8A6").opacity(0.09))
        case .dockhand:                return ServiceColorSet(primary: Color(hex: "#1E88E5"), dark: Color(hex: "#1565C0"), bg: Color(hex: "#1E88E5").opacity(0.09))
        case .dockmon:                 return ServiceColorSet(primary: Color(hex: "#0EA5E9"), dark: Color(hex: "#0369A1"), bg: Color(hex: "#0EA5E9").opacity(0.09))
        case .komodo:                  return ServiceColorSet(primary: Color(hex: "#F97316"), dark: Color(hex: "#C2410C"), bg: Color(hex: "#F97316").opacity(0.08))
        case .maltrail:                return ServiceColorSet(primary: Color(hex: "#DC2626"), dark: Color(hex: "#991B1B"), bg: Color(hex: "#DC2626").opacity(0.08))
        case .uptimeKuma:              return ServiceColorSet(primary: Color(hex: "#22C55E"), dark: Color(hex: "#15803D"), bg: Color(hex: "#22C55E").opacity(0.09))
        case .craftyController:        return ServiceColorSet(primary: Color(hex: "#2E86FF"), dark: Color(hex: "#1E63C6"), bg: Color(hex: "#2E86FF").opacity(0.09))
        case .unifiNetwork:            return ServiceColorSet(primary: Color(hex: "#006FFF"), dark: Color(hex: "#0057D8"), bg: Color(hex: "#006FFF").opacity(0.09))
        case .gitea:              return ServiceColorSet(primary: Color(hex: "#609926"), dark: Color(hex: "#4A7A1E"), bg: Color(hex: "#609926").opacity(0.09))
        case .nginxProxyManager:  return ServiceColorSet(primary: Color(hex: "#F15B2A"), dark: Color(hex: "#C9481F"), bg: Color(hex: "#F15B2A").opacity(0.09))
        case .pangolin:           return ServiceColorSet(primary: Color(hex: "#FF8A3D"), dark: Color(hex: "#D96A22"), bg: Color(hex: "#FF8A3D").opacity(0.10))
        case .patchmon:           return ServiceColorSet(primary: Color(hex: "#2563EB"), dark: Color(hex: "#1D4ED8"), bg: Color(hex: "#2563EB").opacity(0.09))
        case .jellystat:          return ServiceColorSet(primary: Color(hex: "#C93DF6"), dark: Color(hex: "#A92ED0"), bg: Color(hex: "#C93DF6").opacity(0.11))
        case .plex:               return ServiceColorSet(primary: Color(hex: "#E5A00D"), dark: Color(hex: "#CC8E0A"), bg: Color(hex: "#E5A00D").opacity(0.09))
        case .radarr:             return ServiceColorSet(primary: Color(hex: "#FFC230"), dark: Color(hex: "#E5A00D"), bg: Color(hex: "#FFC230").opacity(0.09))
        case .sonarr:             return ServiceColorSet(primary: Color(hex: "#89C5CF"), dark: Color(hex: "#0084A1"), bg: Color(hex: "#89C5CF").opacity(0.09))
        case .lidarr:             return ServiceColorSet(primary: Color(hex: "#006B3E"), dark: Color(hex: "#004B2B"), bg: Color(hex: "#006B3E").opacity(0.09))
        case .qbittorrent:        return ServiceColorSet(primary: Color(hex: "#2C86C1"), dark: Color(hex: "#1B5D8B"), bg: Color(hex: "#2C86C1").opacity(0.09))
        case .jellyseerr:         return ServiceColorSet(primary: Color(hex: "#6C63FF"), dark: Color(hex: "#5548CC"), bg: Color(hex: "#6C63FF").opacity(0.09))
        case .prowlarr:           return ServiceColorSet(primary: Color(hex: "#F97316"), dark: Color(hex: "#C95712"), bg: Color(hex: "#F97316").opacity(0.09))
        case .bazarr:             return ServiceColorSet(primary: Color(hex: "#2563EB"), dark: Color(hex: "#1D4ED8"), bg: Color(hex: "#2563EB").opacity(0.09))
        case .gluetun:            return ServiceColorSet(primary: Color(hex: "#06B6D4"), dark: Color(hex: "#0891B2"), bg: Color(hex: "#06B6D4").opacity(0.09))
        case .flaresolverr:       return ServiceColorSet(primary: Color(hex: "#FF4500"), dark: Color(hex: "#CC3700"), bg: Color(hex: "#FF4500").opacity(0.09))
        case .wakapi:             return ServiceColorSet(primary: Color(hex: "#2563EB"), dark: Color(hex: "#1D4ED8"), bg: Color(hex: "#2563EB").opacity(0.09))
        case .proxmox:            return ServiceColorSet(primary: Color(hex: "#D97706"), dark: Color(hex: "#B45309"), bg: Color(hex: "#D97706").opacity(0.06))
        case .proxmoxBackupServer: return ServiceColorSet(primary: Color(hex: "#D97706"), dark: Color(hex: "#B45309"), bg: Color(hex: "#D97706").opacity(0.06))
        case .prometheus:         return ServiceColorSet(primary: Color(hex: "#E6522C"), dark: Color(hex: "#B93F22"), bg: Color(hex: "#E6522C").opacity(0.09))
        case .grafana:            return ServiceColorSet(primary: Color(hex: "#F46800"), dark: Color(hex: "#C45100"), bg: Color(hex: "#F46800").opacity(0.09))
        case .netbox:             return ServiceColorSet(primary: Color(hex: "#00A651"), dark: Color(hex: "#007A3D"), bg: Color(hex: "#00A651").opacity(0.09))
        case .zammad:             return ServiceColorSet(primary: Color(hex: "#CA2317"), dark: Color(hex: "#991B12"), bg: Color(hex: "#CA2317").opacity(0.09))
        case .pegaprox:           return ServiceColorSet(primary: Color(hex: "#5B4BDB"), dark: Color(hex: "#4338A8"), bg: Color(hex: "#5B4BDB").opacity(0.09))
        case .opnsense:           return ServiceColorSet(primary: Color(hex: "#D94F00"), dark: Color(hex: "#A83D00"), bg: Color(hex: "#D94F00").opacity(0.09))
        case .oneuptime:          return ServiceColorSet(primary: Color(hex: "#5B5BD6"), dark: Color(hex: "#4141A8"), bg: Color(hex: "#5B5BD6").opacity(0.09))
        case .truenas:            return ServiceColorSet(primary: .truenasAccessibleAccent, dark: Color(hex: "#006EA3"), bg: Color(hex: "#0095D5").opacity(0.09))
        case .pterodactyl:        return ServiceColorSet(primary: Color(hex: "#0E4BEF"), dark: Color(hex: "#0B38C5"), bg: Color(hex: "#0E4BEF").opacity(0.09))
        case .calagopus:          return ServiceColorSet(primary: Color(hex: "#16A34A"), dark: Color(hex: "#15803D"), bg: Color(hex: "#16A34A").opacity(0.09))
        }
    }
}

enum ProviderCapability: String, Codable, CaseIterable, Hashable, Sendable {
    case health
    case resources
    case events
    case metrics
    case readActions = "read_actions"
    case writeActions = "write_actions"
}

enum ProviderHealthState: String, Codable, Hashable, Sendable {
    case healthy
    case degraded
    case unavailable
    case unknown
}

struct ProviderHealth: Codable, Equatable, Sendable {
    let providerId: String
    let instanceId: UUID
    let state: ProviderHealthState
    let message: String?
    let observedAt: Date
    let attributes: [String: String]
}

struct ProviderResource: Codable, Equatable, Sendable {
    let providerId: String
    let instanceId: UUID
    let resourceType: String
    let resourceId: String
    let name: String
    let state: String?
    let attributes: [String: String]
}

struct ProviderEvent: Codable, Equatable, Sendable {
    let providerId: String
    let instanceId: UUID
    let eventId: String
    let severity: String
    let message: String
    let occurredAt: Date
    let resourceId: String?
}

struct ProviderDiagnostic: Codable, Equatable, Sendable {
    let providerId: String
    let instanceId: UUID
    let displayName: String
    let endpoint: String
    let tlsMode: TLSMode
    let capabilities: Set<ProviderCapability>
    let state: ProviderHealthState
    let message: String?
    let observedAt: Date
}

struct OperationsSnapshot: Codable, Equatable, Sendable {
    var health: [ProviderHealth] = []
    var alerts: [ProviderEvent] = []
    var assets: [ProviderResource] = []
    var diagnostics: [ProviderDiagnostic] = []
    var refreshedAt = Date()

    func search(_ query: String) -> OperationsSearchResults {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !needle.isEmpty else { return OperationsSearchResults() }
        func matches(_ value: String?) -> Bool { value?.lowercased().contains(needle) == true }
        return OperationsSearchResults(
            health: health.filter { matches($0.providerId) || matches($0.instanceId.uuidString) || matches($0.message) },
            alerts: alerts.filter { matches($0.providerId) || matches($0.message) || matches($0.resourceId) },
            assets: assets.filter { asset in
                matches(asset.providerId) || matches(asset.resourceType) || matches(asset.resourceId) ||
                    matches(asset.name) || matches(asset.state) || asset.attributes.contains { matches($0.key) || matches($0.value) }
            },
            diagnostics: diagnostics.filter { matches($0.providerId) || matches($0.displayName) || matches($0.endpoint) || matches($0.message) }
        )
    }
}

struct OperationsSearchResults: Equatable, Sendable {
    var health: [ProviderHealth] = []
    var alerts: [ProviderEvent] = []
    var assets: [ProviderResource] = []
    var diagnostics: [ProviderDiagnostic] = []

    var isEmpty: Bool { health.isEmpty && alerts.isEmpty && assets.isEmpty && diagnostics.isEmpty }
}

struct ProviderDescriptor: Equatable, Sendable {
    let id: String
    let serviceType: ServiceType
    let displayName: String
    let capabilities: Set<ProviderCapability>
}

enum ProviderRegistry {
    private static let descriptors: [ServiceType: ProviderDescriptor] = Dictionary(
        uniqueKeysWithValues: ServiceType.allCases.map { type in
            let capabilities: Set<ProviderCapability>
            switch type {
            case .proxmox, .portainer:
                capabilities = [.health, .resources, .events, .metrics, .readActions, .writeActions]
            case .adguardHome:
                capabilities = [.health, .writeActions]
            case .pihole:
                capabilities = [.health, .writeActions]
            case .technitium:
                capabilities = [.health, .writeActions]
            case .healthchecks, .dockhand, .dockmon, .linuxUpdate, .komodo, .nginxProxyManager:
                capabilities = [.health, .writeActions]
            case .pangolin:
                capabilities = [.health, .writeActions]
            case .qbittorrent:
                capabilities = [.health, .writeActions]
            case .patchmon:
                capabilities = [.health, .writeActions]
            case .pterodactyl, .calagopus, .craftyController:
                capabilities = [.health, .writeActions]
            case .proxmoxBackupServer:
                capabilities = [.health, .resources, .events, .metrics]
            case .prometheus:
                capabilities = [.health, .resources, .events, .metrics]
            case .grafana:
                capabilities = [.health, .resources, .metrics]
            case .netbox:
                capabilities = [.health, .resources]
            case .zammad:
                capabilities = [.health, .resources, .events, .readActions]
            case .pegaprox:
                capabilities = [.health, .resources, .events, .metrics, .readActions]
            case .opnsense:
                capabilities = [.health, .resources, .metrics, .readActions]
            case .oneuptime:
                capabilities = [.health, .resources, .events, .readActions]
            case .uptimeKuma:
                capabilities = [.health, .resources, .events, .metrics]
            default:
                capabilities = [.health]
            }
            return (
                type,
                ProviderDescriptor(
                    id: type.rawValue.replacingOccurrences(of: "_", with: "-"),
                    serviceType: type,
                    displayName: type.displayName,
                    capabilities: capabilities
                )
            )
        }
    )

    static func descriptor(for type: ServiceType) -> ProviderDescriptor {
        precondition(descriptors[type] != nil, "Every ServiceType must be registered")
        return descriptors[type]!
    }

    static var registeredProviders: [ProviderDescriptor] {
        descriptors.values.sorted { $0.id < $1.id }
    }
}

public struct ServiceColorSet {
    public let primary: Color
    public let dark: Color
    public let bg: Color

    public init(primary: Color, dark: Color, bg: Color) {
        self.primary = primary
        self.dark = dark
        self.bg = bg
    }
}
enum ControlledActionRisk: String, Codable, CaseIterable, Equatable, Sendable {
    case low
    case medium
    case high
    case critical
}

enum ProxmoxControlledGuestAction: String, CaseIterable, Equatable, Sendable {
    case start, stop, shutdown, reboot, suspend, resume

    var actionName: String { "guest.\(rawValue)" }

    var risk: ControlledActionRisk {
        switch self {
        case .start, .resume: return .low
        case .shutdown, .reboot, .suspend: return .medium
        case .stop: return .high
        }
    }

    func request(
        instanceId: UUID,
        node: String,
        vmid: Int,
        guestType: ProxmoxGuestType,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "proxmox:\(instanceId.uuidString.lowercased())",
            action: actionName,
            targetRef: "\(guestType.rawValue)/\(vmid)@\(node)",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum PortainerControlledContainerAction: String, CaseIterable, Equatable, Sendable {
    case start, stop, restart, kill, pause, resume, remove

    var actionName: String { "container.\(rawValue)" }

    var risk: ControlledActionRisk {
        switch self {
        case .start: return .low
        case .stop, .restart, .pause, .resume: return .medium
        case .kill, .remove: return .high
        }
    }

    var requiresConfirmation: Bool { risk != .low }

    func request(
        instanceId: UUID,
        endpointId: Int,
        containerId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "portainer:\(instanceId.uuidString.lowercased())",
            action: actionName,
            targetRef: "endpoint/\(endpointId)/container/\(containerId)",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum PortainerControlledConfigurationAction: String, CaseIterable, Equatable, Sendable {
    case renameContainer = "container.rename"
    case updateStack = "stack.update"

    var risk: ControlledActionRisk {
        switch self {
        case .renameContainer: return .medium
        case .updateStack: return .high
        }
    }

    var requiresConfirmation: Bool { true }

    func request(
        instanceId: UUID,
        endpointId: Int,
        targetId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        let targetKind: String
        switch self {
        case .renameContainer:
            targetKind = "container"
        case .updateStack:
            targetKind = "stack"
        }
        return ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "portainer:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: "endpoint/\(endpointId)/\(targetKind)/\(targetId.trimmingCharacters(in: .whitespacesAndNewlines))",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum DockhandControlledAction: String, CaseIterable, Equatable, Sendable {
    case containerStart = "container.start"
    case containerStop = "container.stop"
    case containerRestart = "container.restart"
    case stackStart = "stack.start"
    case stackStop = "stack.stop"
    case stackRestart = "stack.restart"

    var risk: ControlledActionRisk {
        switch self {
        case .containerStart, .stackStart: return .low
        case .containerStop, .containerRestart, .stackStop, .stackRestart: return .medium
        }
    }

    var requiresConfirmation: Bool { risk != .low }

    func request(
        instanceId: UUID,
        environmentId: String?,
        targetKind: String,
        targetId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        let environment = environmentId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let normalizedTarget = targetId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "dockhand:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: "environment/\(environment?.isEmpty == false ? environment! : "default")/\(targetKind)/\(normalizedTarget)",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum DockmonControlledAction: String, CaseIterable, Equatable, Sendable {
    case restart = "container.restart"
    case update = "container.update"

    var risk: ControlledActionRisk { self == .restart ? .medium : .high }
    var requiresConfirmation: Bool { true }

    func request(
        instanceId: UUID,
        containerId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "dockmon:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: "container/\(containerId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum LinuxUpdateControlledAction: String, CaseIterable, Equatable, Sendable {
    case checkAll = "systems.check-all"
    case refreshCache = "cache.refresh"
    case checkSystem = "system.check"
    case upgradePackage = "package.upgrade"
    case upgradeAll = "system.upgrade"
    case fullUpgrade = "system.full-upgrade"
    case reboot = "system.reboot"

    var risk: ControlledActionRisk {
        switch self {
        case .checkAll, .refreshCache, .checkSystem: return .low
        case .upgradePackage: return .medium
        case .upgradeAll, .fullUpgrade, .reboot: return .high
        }
    }

    var requiresConfirmation: Bool { risk != .low }

    func targetRef(systemId: Int? = nil, packageName: String? = nil) -> String {
        switch self {
        case .checkAll:
            return "systems/all"
        case .refreshCache:
            return "cache/global"
        case .upgradePackage:
            let package = packageName?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
            return "system/\(systemId ?? 0)/package/\(package)"
        case .checkSystem, .upgradeAll, .fullUpgrade, .reboot:
            return "system/\(systemId ?? 0)"
        }
    }

    func request(
        instanceId: UUID,
        targetRef: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "linux-update:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: targetRef.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum TechnitiumControlledAction: String, CaseIterable, Equatable, Sendable {
    case enableBlocking = "blocking.enable"
    case disableBlocking = "blocking.disable"
    case temporaryDisable = "blocking.disable-temporary"
    case refreshBlockLists = "blocklist.refresh"
    case addBlockedDomain = "blocked-domain.add"
    case removeBlockedDomain = "blocked-domain.remove"

    var risk: ControlledActionRisk {
        switch self {
        case .enableBlocking, .refreshBlockLists: return .low
        case .disableBlocking, .temporaryDisable, .removeBlockedDomain: return .medium
        case .addBlockedDomain: return .high
        }
    }

    var requiresConfirmation: Bool { risk != .low }

    func request(
        instanceId: UUID,
        targetRef: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "technitium:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: targetRef.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum NpmProxyHostControlledAction: String, CaseIterable, Equatable, Sendable {
    case create = "proxy-host.create"
    case update = "proxy-host.update"
    case enable = "proxy-host.enable"
    case disable = "proxy-host.disable"
    case delete = "proxy-host.delete"

    var risk: ControlledActionRisk {
        switch self {
        case .enable: return .low
        case .disable: return .medium
        case .create, .update, .delete: return .high
        }
    }

    var requiresConfirmation: Bool { risk != .low }

    func request(
        instanceId: UUID,
        hostId: Int?,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "nginx-proxy-manager:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: "proxy-host/\(hostId.map(String.init) ?? "new")",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum NpmConfigurationControlledAction: String, CaseIterable, Equatable, Sendable {
    case createRedirectionHost = "redirection-host.create"
    case updateRedirectionHost = "redirection-host.update"
    case deleteRedirectionHost = "redirection-host.delete"
    case createStream = "stream.create"
    case updateStream = "stream.update"
    case deleteStream = "stream.delete"
    case createDeadHost = "dead-host.create"
    case updateDeadHost = "dead-host.update"
    case deleteDeadHost = "dead-host.delete"
    case createCertificate = "certificate.create"
    case renewCertificate = "certificate.renew"
    case deleteCertificate = "certificate.delete"
    case createAccessList = "access-list.create"
    case updateAccessList = "access-list.update"
    case deleteAccessList = "access-list.delete"
    case createUser = "user.create"
    case updateUser = "user.update"
    case deleteUser = "user.delete"

    var targetKind: String {
        switch self {
        case .createRedirectionHost, .updateRedirectionHost, .deleteRedirectionHost: return "redirection-host"
        case .createStream, .updateStream, .deleteStream: return "stream"
        case .createDeadHost, .updateDeadHost, .deleteDeadHost: return "dead-host"
        case .createCertificate, .renewCertificate, .deleteCertificate: return "certificate"
        case .createAccessList, .updateAccessList, .deleteAccessList: return "access-list"
        case .createUser, .updateUser, .deleteUser: return "user"
        }
    }

    var risk: ControlledActionRisk {
        self == .renewCertificate ? .medium : .high
    }

    var requiresConfirmation: Bool { risk != .low }

    func request(
        instanceId: UUID,
        targetId: Int?,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "nginx-proxy-manager:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: "\(targetKind)/\(targetId.map(String.init) ?? "new")",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum AdGuardControlledProtectionAction: String, CaseIterable, Equatable, Sendable {
    case enable, disable

    var actionName: String { "protection.\(rawValue)" }
    var risk: ControlledActionRisk { self == .enable ? .low : .medium }

    func request(
        instanceId: UUID,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "adguard-home:\(instanceId.uuidString.lowercased())",
            action: actionName,
            targetRef: "protection/global",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum AdGuardControlledConfigurationAction: String, CaseIterable, Equatable, Sendable {
    case updateUserRules = "filtering.user-rules.update"
    case createFilter = "filter-list.create"
    case updateFilter = "filter-list.update"
    case deleteFilter = "filter-list.delete"
    case updateBlockedServices = "blocked-services.update"
    case createRewrite = "rewrite.create"
    case updateRewrite = "rewrite.update"
    case deleteRewrite = "rewrite.delete"

    var targetKind: String {
        switch self {
        case .updateUserRules: return "user-rules"
        case .createFilter, .updateFilter, .deleteFilter: return "filter-list"
        case .updateBlockedServices: return "blocked-services"
        case .createRewrite, .updateRewrite, .deleteRewrite: return "rewrite"
        }
    }

    var risk: ControlledActionRisk { .high }

    func request(
        instanceId: UUID,
        targetId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "adguard-home:\(instanceId.uuidString.lowercased())",
            action: rawValue,
            targetRef: "\(targetKind)/\(targetId)",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}
enum HealthchecksControlledCheckAction: String, CaseIterable, Equatable, Sendable {
    case create, update, updateChannels = "channels.update", pause, resume, delete

    var actionName: String { "check.\(rawValue)" }

    var risk: ControlledActionRisk {
        switch self {
        case .update, .updateChannels, .pause, .resume: return .medium
        case .create, .delete: return .high
        }
    }

    func request(
        instanceId: UUID,
        checkId: String,
        confirmed: Bool,
        requestId: UUID = UUID(),
        requestedAt: Date = Date(),
        idempotencyKey: UUID = UUID()
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: requestId.uuidString,
            providerRef: "healthchecks:\(instanceId.uuidString.lowercased())",
            action: actionName,
            targetRef: "check/\(checkId)",
            risk: risk,
            requestedAt: ISO8601DateFormatter().string(from: requestedAt),
            idempotencyKey: idempotencyKey.uuidString,
            confirmed: confirmed
        )
    }
}

enum ControlledActionRole: String, Codable, CaseIterable, Equatable, Sendable {
    case viewer
    case operatorRole = "operator"
    case admin

    var level: Int {
        switch self {
        case .viewer: return 0
        case .operatorRole: return 1
        case .admin: return 2
        }
    }
}

struct ControlledActionRequest: Codable, Equatable, Sendable {
    var schemaVersion: String
    let id: String
    let providerRef: String
    var tenantRef: String?
    let action: String
    let targetRef: String
    let risk: ControlledActionRisk
    let requestedAt: String
    let idempotencyKey: String
    var parameters: [String: String]
    var dryRun: Bool
    var confirmed: Bool

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, id, providerRef, tenantRef, action, targetRef, risk
        case requestedAt, idempotencyKey, parameters, dryRun, confirmed
    }

    init(
        schemaVersion: String = "1.0",
        id: String,
        providerRef: String,
        tenantRef: String? = nil,
        action: String,
        targetRef: String,
        risk: ControlledActionRisk,
        requestedAt: String,
        idempotencyKey: String,
        parameters: [String: String] = [:],
        dryRun: Bool = false,
        confirmed: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.id = id
        self.providerRef = providerRef
        self.tenantRef = tenantRef
        self.action = action
        self.targetRef = targetRef
        self.risk = risk
        self.requestedAt = requestedAt
        self.idempotencyKey = idempotencyKey
        self.parameters = parameters
        self.dryRun = dryRun
        self.confirmed = confirmed
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decode(String.self, forKey: .schemaVersion)
        id = try container.decode(String.self, forKey: .id)
        providerRef = try container.decode(String.self, forKey: .providerRef)
        tenantRef = try container.decodeIfPresent(String.self, forKey: .tenantRef)
        action = try container.decode(String.self, forKey: .action)
        targetRef = try container.decode(String.self, forKey: .targetRef)
        risk = try container.decode(ControlledActionRisk.self, forKey: .risk)
        requestedAt = try container.decode(String.self, forKey: .requestedAt)
        idempotencyKey = try container.decode(String.self, forKey: .idempotencyKey)
        parameters = try container.decodeIfPresent([String: String].self, forKey: .parameters) ?? [:]
        dryRun = try container.decodeIfPresent(Bool.self, forKey: .dryRun) ?? false
        confirmed = try container.decodeIfPresent(Bool.self, forKey: .confirmed) ?? false
    }
}

enum ActionPolicyOutcome: String, Codable, Equatable, Sendable {
    case denied
    case confirmationRequired = "confirmation_required"
    case approved
    case dryRunApproved = "dry_run_approved"
}

struct ActionPolicyDecision: Codable, Equatable, Sendable {
    let outcome: ActionPolicyOutcome
    let reasonCode: String
    let requiredRole: ControlledActionRole
    let confirmationRequired: Bool

    var mayExecute: Bool { outcome == .approved }
}

enum ControlledActionPolicy {
    static func evaluate(
        _ request: ControlledActionRequest,
        actorRole: ControlledActionRole,
        providerCapabilities: Set<ProviderCapability>
    ) -> ActionPolicyDecision {
        let requiredRole: ControlledActionRole
        switch request.risk {
        case .low, .medium: requiredRole = .operatorRole
        case .high, .critical: requiredRole = .admin
        }
        let confirmationRequired = request.risk != .low

        let invalidReason: String?
        if request.schemaVersion != "1.0" {
            invalidReason = "unsupported-schema-version"
        } else if request.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            invalidReason = "invalid-request-id"
        } else if request.providerRef.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            invalidReason = "invalid-provider-ref"
        } else if request.targetRef.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            invalidReason = "invalid-target-ref"
        } else if request.requestedAt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            invalidReason = "invalid-requested-at"
        } else if request.action.range(
            of: "^[a-z][a-z0-9.-]{1,127}$",
            options: .regularExpression
        ) == nil {
            invalidReason = "invalid-action"
        } else if !(16...256).contains(request.idempotencyKey.count) {
            invalidReason = "invalid-idempotency-key"
        } else {
            invalidReason = nil
        }

        if let invalidReason {
            return ActionPolicyDecision(
                outcome: .denied,
                reasonCode: invalidReason,
                requiredRole: requiredRole,
                confirmationRequired: confirmationRequired
            )
        }
        guard providerCapabilities.contains(.writeActions) else {
            return ActionPolicyDecision(
                outcome: .denied,
                reasonCode: "provider-write-capability-required",
                requiredRole: requiredRole,
                confirmationRequired: confirmationRequired
            )
        }
        guard actorRole.level >= requiredRole.level else {
            return ActionPolicyDecision(
                outcome: .denied,
                reasonCode: "insufficient-role",
                requiredRole: requiredRole,
                confirmationRequired: confirmationRequired
            )
        }
        if request.dryRun {
            return ActionPolicyDecision(
                outcome: .dryRunApproved,
                reasonCode: "dry-run-validated",
                requiredRole: requiredRole,
                confirmationRequired: confirmationRequired
            )
        }
        if confirmationRequired && !request.confirmed {
            return ActionPolicyDecision(
                outcome: .confirmationRequired,
                reasonCode: "explicit-confirmation-required",
                requiredRole: requiredRole,
                confirmationRequired: true
            )
        }
        return ActionPolicyDecision(
            outcome: .approved,
            reasonCode: "policy-approved",
            requiredRole: requiredRole,
            confirmationRequired: confirmationRequired
        )
    }
}

enum ActionExecutionState: String, Codable, Equatable, Sendable {
    case queued, executing
    case retryWait = "retry_wait"
    case succeeded, failed, cancelled, rejected
    case dryRun = "dry_run"
    case manualReview = "manual_review"
}

struct ActionAuditRecord: Codable, Equatable, Sendable {
    let auditId: UUID
    let requestId: String
    let providerRef: String
    let action: String
    let targetRef: String
    let risk: ControlledActionRisk
    let actorRole: ControlledActionRole
    let idempotencyKey: String
    let state: ActionExecutionState
    let reasonCode: String
    let recordedAt: Date
}

struct DurableActionQueueEntry: Codable, Equatable, Sendable {
    let request: ControlledActionRequest
    let actorRole: ControlledActionRole
    var state: ActionExecutionState
    var attemptCount: Int
    var nextAttemptAt: Date?
    var reasonCode: String
    var updatedAt: Date
    var terminalRecord: ActionAuditRecord?
}

protocol DurableActionQueueStore: Sendable {
    func snapshot() async -> [DurableActionQueueEntry]
    func upsert(_ entry: DurableActionQueueEntry) async
}

actor InMemoryDurableActionQueueStore: DurableActionQueueStore {
    private let maximumEntries: Int
    private var entries: [String: DurableActionQueueEntry] = [:]
    private var order: [String] = []

    init(initialEntries: [DurableActionQueueEntry] = [], maximumEntries: Int = 500) {
        precondition(maximumEntries > 0)
        self.maximumEntries = maximumEntries
        for entry in initialEntries.suffix(maximumEntries) {
            entries[entry.request.idempotencyKey] = entry
            order.append(entry.request.idempotencyKey)
        }
    }

    func snapshot() -> [DurableActionQueueEntry] {
        order.compactMap { entries[$0] }
    }

    func upsert(_ entry: DurableActionQueueEntry) {
        let key = entry.request.idempotencyKey
        order.removeAll { $0 == key }
        order.append(key)
        entries[key] = entry
        while order.count > maximumEntries {
            entries.removeValue(forKey: order.removeFirst())
        }
    }
}

actor UserDefaultsDurableActionQueueStore: DurableActionQueueStore {
    private let key: String
    private let maximumEntries: Int

    init(key: String = "controlled_action_queue_v1", maximumEntries: Int = 500) {
        precondition(maximumEntries > 0)
        self.key = key
        self.maximumEntries = maximumEntries
    }

    func snapshot() -> [DurableActionQueueEntry] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([DurableActionQueueEntry].self, from: data)) ?? []
    }

    func upsert(_ entry: DurableActionQueueEntry) {
        var entries = snapshotByKey()
        entries.removeAll { $0.request.idempotencyKey == entry.request.idempotencyKey }
        entries.append(entry)
        if entries.count > maximumEntries {
            entries.removeFirst(entries.count - maximumEntries)
        }
        guard let encoded = try? JSONEncoder().encode(entries) else { return }
        UserDefaults.standard.set(encoded, forKey: key)
    }

    private func snapshotByKey() -> [DurableActionQueueEntry] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([DurableActionQueueEntry].self, from: data)) ?? []
    }
}

struct ActionRetryPolicy: Codable, Equatable, Sendable {
    let maximumAttempts: Int
    let initialDelayMilliseconds: Int
    let maximumDelayMilliseconds: Int

    init(
        maximumAttempts: Int = 3,
        initialDelayMilliseconds: Int = 1_000,
        maximumDelayMilliseconds: Int = 30_000
    ) {
        precondition(maximumAttempts > 0)
        precondition(initialDelayMilliseconds >= 0)
        precondition(maximumDelayMilliseconds >= initialDelayMilliseconds)
        self.maximumAttempts = maximumAttempts
        self.initialDelayMilliseconds = initialDelayMilliseconds
        self.maximumDelayMilliseconds = maximumDelayMilliseconds
    }

    func delayBeforeAttempt(completedAttempts: Int) -> Int {
        guard completedAttempts > 0 else { return 0 }
        var value = initialDelayMilliseconds
        for _ in 0..<min(max(completedAttempts - 1, 0), 30) {
            value = min(value * 2, maximumDelayMilliseconds)
        }
        return value
    }

    func permitsAutomaticRetry(risk: ControlledActionRisk, completedAttempts: Int) -> Bool {
        (risk == .low || risk == .medium) && completedAttempts < maximumAttempts
    }
}

enum ActionFailureDisposition: String, Codable, Equatable, Sendable {
    case retryable
    case nonRetryable = "non_retryable"
}

struct ControlledActionOperationError: Error, Sendable {
    let reasonCode: String
    let disposition: ActionFailureDisposition
}

enum PortainerControlledOperationFailure {
    static func map(_ error: Error) -> ControlledActionOperationError {
        if let controlled = error as? ControlledActionOperationError {
            return controlled
        }
        if error is URLError {
            return indeterminateOutcome()
        }
        guard let apiError = error as? APIError else {
            return providerReportedFailure()
        }

        switch apiError {
        case .networkError:
            return indeterminateOutcome()
        case .bothURLsFailed:
            return indeterminateOutcome()
        case .unauthorized:
            return ControlledActionOperationError(
                reasonCode: "portainer-unauthorized",
                disposition: .nonRetryable
            )
        case .httpError(let statusCode, _):
            return ControlledActionOperationError(
                reasonCode: "portainer-http-\(statusCode)",
                disposition: .nonRetryable
            )
        case .notConfigured:
            return ControlledActionOperationError(
                reasonCode: "portainer-not-configured",
                disposition: .nonRetryable
            )
        case .invalidURL:
            return ControlledActionOperationError(
                reasonCode: "portainer-invalid-url",
                disposition: .nonRetryable
            )
        case .decodingError:
            return ControlledActionOperationError(
                reasonCode: "portainer-response-decode-failure",
                disposition: .nonRetryable
            )
        case .requestConfigurationRequired:
            return ControlledActionOperationError(
                reasonCode: "portainer-configuration-required",
                disposition: .nonRetryable
            )
        case .custom:
            return providerReportedFailure()
        }
    }

    private static func indeterminateOutcome() -> ControlledActionOperationError {
        ControlledActionOperationError(
            reasonCode: "portainer-outcome-indeterminate",
            disposition: .nonRetryable
        )
    }

    private static func providerReportedFailure() -> ControlledActionOperationError {
        ControlledActionOperationError(
            reasonCode: "portainer-provider-reported-failure",
            disposition: .nonRetryable
        )
    }
}

actor ControlledActionLedger {
    private let maximumRecords: Int
    private var records: [ActionAuditRecord] = []

    init(maximumRecords: Int = 500) {
        precondition(maximumRecords > 0)
        self.maximumRecords = maximumRecords
    }

    func append(_ record: ActionAuditRecord) {
        records.append(record)
        if records.count > maximumRecords {
            records.removeFirst(records.count - maximumRecords)
        }
    }

    func snapshot() -> [ActionAuditRecord] { records }
}

actor ControlledActionExecutionGate {
    private var isLocked = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func withLock<T: Sendable>(_ operation: @Sendable () async -> T) async -> T {
        await acquire()
        let result = await operation()
        release()
        return result
    }

    private func acquire() async {
        if !isLocked {
            isLocked = true
            return
        }
        await withCheckedContinuation { waiters.append($0) }
    }

    private func release() {
        guard !waiters.isEmpty else {
            isLocked = false
            return
        }
        waiters.removeFirst().resume()
    }
}

actor ControlledActionCoordinator {
    private let ledger: ControlledActionLedger
    private let executionGate: ControlledActionExecutionGate
    private let durableStore: any DurableActionQueueStore
    private let retryPolicy: ActionRetryPolicy
    private let now: @Sendable () -> Date
    private let waitBeforeRetry: @Sendable (Int) async -> Void
    private var terminalResults: [String: ActionAuditRecord] = [:]
    private var durableEntries: [String: DurableActionQueueEntry] = [:]
    private var inFlight: [String: Task<ActionAuditRecord, Never>] = [:]
    private var recovered = false

    init(
        ledger: ControlledActionLedger = ControlledActionLedger(),
        executionGate: ControlledActionExecutionGate = ControlledActionExecutionGate(),
        durableStore: any DurableActionQueueStore = InMemoryDurableActionQueueStore(),
        retryPolicy: ActionRetryPolicy = ActionRetryPolicy(),
        now: @escaping @Sendable () -> Date = Date.init,
        waitBeforeRetry: @escaping @Sendable (Int) async -> Void = {
            try? await Task.sleep(nanoseconds: UInt64($0) * 1_000_000)
        }
    ) {
        self.ledger = ledger
        self.executionGate = executionGate
        self.durableStore = durableStore
        self.retryPolicy = retryPolicy
        self.now = now
        self.waitBeforeRetry = waitBeforeRetry
    }

    func execute(
        request: ControlledActionRequest,
        actorRole: ControlledActionRole,
        providerCapabilities: Set<ProviderCapability>,
        operation: @escaping @Sendable () async throws -> Void
    ) async -> ActionAuditRecord {
        await recoverIfNeeded()
        let existing = durableEntries[request.idempotencyKey]
        if let existing, !existing.request.hasSameIdentity(as: request) {
            return await Self.audit(
                request: request, actorRole: actorRole, state: .rejected,
                reasonCode: "idempotency-key-conflict", ledger: ledger, now: now
            )
        }
        if let completed = terminalResults[request.idempotencyKey] { return completed }
        if let existingTask = inFlight[request.idempotencyKey] { return await existingTask.value }
        if let existing, existing.state == .manualReview {
            let result: ActionAuditRecord
            if let terminal = existing.terminalRecord {
                result = terminal
            } else {
                result = await Self.audit(
                    request: request, actorRole: actorRole, state: .manualReview,
                    reasonCode: existing.reasonCode, ledger: ledger, now: now
                )
            }
            terminalResults[request.idempotencyKey] = result
            return result
        }

        let task = Task { [executionGate, ledger, durableStore, retryPolicy, now, waitBeforeRetry] in
            await executionGate.withLock {
                await Self.perform(
                    request: request,
                    actorRole: actorRole,
                    providerCapabilities: providerCapabilities,
                    existing: existing,
                    ledger: ledger,
                    durableStore: durableStore,
                    retryPolicy: retryPolicy,
                    now: now,
                    waitBeforeRetry: waitBeforeRetry,
                    operation: operation
                )
            }
        }
        inFlight[request.idempotencyKey] = task
        let result = await task.value
        terminalResults[request.idempotencyKey] = result
        inFlight[request.idempotencyKey] = nil
        if let updated = await durableStore.snapshot().first(where: {
            $0.request.idempotencyKey == request.idempotencyKey
        }) {
            durableEntries[request.idempotencyKey] = updated
        }
        return result
    }

    func pendingRecovery() async -> [DurableActionQueueEntry] {
        await recoverIfNeeded()
        return durableEntries.values.filter {
            $0.state == .queued || $0.state == .retryWait || $0.state == .manualReview
        }
    }

    func auditSnapshot() async -> [ActionAuditRecord] { await ledger.snapshot() }

    private func recoverIfNeeded() async {
        guard !recovered else { return }
        for stored in await durableStore.snapshot() {
            var entry = stored
            if stored.state == .executing {
                let result = await Self.audit(
                    request: stored.request,
                    actorRole: stored.actorRole,
                    state: .manualReview,
                    reasonCode: "interrupted-execution",
                    ledger: ledger,
                    now: now
                )
                entry.state = .manualReview
                entry.reasonCode = "interrupted-execution"
                entry.updatedAt = now()
                entry.terminalRecord = result
                await durableStore.upsert(entry)
            }
            durableEntries[entry.request.idempotencyKey] = entry
            if let terminal = entry.terminalRecord {
                terminalResults[entry.request.idempotencyKey] = terminal
            }
        }
        recovered = true
    }

    private static func perform(
        request: ControlledActionRequest,
        actorRole: ControlledActionRole,
        providerCapabilities: Set<ProviderCapability>,
        existing: DurableActionQueueEntry?,
        ledger: ControlledActionLedger,
        durableStore: any DurableActionQueueStore,
        retryPolicy: ActionRetryPolicy,
        now: @Sendable () -> Date,
        waitBeforeRetry: @Sendable (Int) async -> Void,
        operation: @escaping @Sendable () async throws -> Void
    ) async -> ActionAuditRecord {
        let decision = ControlledActionPolicy.evaluate(
            request, actorRole: actorRole, providerCapabilities: providerCapabilities
        )
        switch decision.outcome {
        case .denied, .confirmationRequired:
            let result = await audit(
                request: request, actorRole: actorRole, state: .rejected,
                reasonCode: decision.reasonCode, ledger: ledger, now: now
            )
            await persistTerminal(request, actorRole, result, 0, durableStore, now)
            return result
        case .dryRunApproved:
            let result = await audit(
                request: request, actorRole: actorRole, state: .dryRun,
                reasonCode: decision.reasonCode, ledger: ledger, now: now
            )
            await persistTerminal(request, actorRole, result, 0, durableStore, now)
            return result
        case .approved:
            break
        }

        var entry = existing ?? DurableActionQueueEntry(
            request: request.sanitizedForPersistence(),
            actorRole: actorRole,
            state: .queued,
            attemptCount: 0,
            reasonCode: "queued",
            updatedAt: now()
        )
        if existing == nil {
            await durableStore.upsert(entry)
            _ = await audit(
                request: request, actorRole: actorRole, state: .queued,
                reasonCode: "queued", ledger: ledger, now: now
            )
        }

        if let retryAt = entry.nextAttemptAt {
            let remaining = max(Int(retryAt.timeIntervalSince(now()) * 1_000), 0)
            if remaining > 0 { await waitBeforeRetry(remaining) }
        }

        while true {
            entry.state = .executing
            entry.attemptCount += 1
            entry.nextAttemptAt = nil
            entry.reasonCode = "executing"
            entry.updatedAt = now()
            await durableStore.upsert(entry)
            _ = await audit(
                request: request, actorRole: actorRole, state: .executing,
                reasonCode: "attempt-\(entry.attemptCount)", ledger: ledger, now: now
            )

            do {
                try await operation()
                let result = await audit(
                    request: request, actorRole: actorRole, state: .succeeded,
                    reasonCode: "completed", ledger: ledger, now: now
                )
                await persistTerminal(request, actorRole, result, entry.attemptCount, durableStore, now)
                return result
            } catch is CancellationError {
                let result = await audit(
                    request: request, actorRole: actorRole, state: .cancelled,
                    reasonCode: "cancelled", ledger: ledger, now: now
                )
                entry.state = .manualReview
                entry.reasonCode = "cancelled-during-execution"
                entry.updatedAt = now()
                entry.terminalRecord = result
                await durableStore.upsert(entry)
                return result
            } catch {
                let failure = classifyFailure(error)
                if failure.disposition == .retryable &&
                    retryPolicy.permitsAutomaticRetry(
                        risk: request.risk, completedAttempts: entry.attemptCount
                    ) {
                    let delay = retryPolicy.delayBeforeAttempt(completedAttempts: entry.attemptCount)
                    entry.state = .retryWait
                    entry.nextAttemptAt = now().addingTimeInterval(Double(delay) / 1_000)
                    entry.reasonCode = failure.reasonCode
                    entry.updatedAt = now()
                    await durableStore.upsert(entry)
                    _ = await audit(
                        request: request, actorRole: actorRole, state: .retryWait,
                        reasonCode: failure.reasonCode, ledger: ledger, now: now
                    )
                    await waitBeforeRetry(delay)
                    continue
                }

                let review = failure.disposition == .retryable
                let state: ActionExecutionState = review ? .manualReview : .failed
                let reason: String
                if review && (request.risk == .high || request.risk == .critical) {
                    reason = "automatic-retry-forbidden-\(failure.reasonCode)"
                } else if review {
                    reason = "retry-exhausted-\(failure.reasonCode)"
                } else {
                    reason = failure.reasonCode
                }
                let result = await audit(
                    request: request, actorRole: actorRole, state: state,
                    reasonCode: reason, ledger: ledger, now: now
                )
                await persistTerminal(request, actorRole, result, entry.attemptCount, durableStore, now)
                return result
            }
        }
    }

    private static func classifyFailure(_ error: Error) -> ControlledActionOperationError {
        if let controlled = error as? ControlledActionOperationError { return controlled }
        if error is URLError {
            return ControlledActionOperationError(reasonCode: "transport-error", disposition: .retryable)
        }
        if let apiError = error as? APIError {
            switch apiError {
            case .networkError(let underlying):
                return classifyFailure(underlying)
            case .bothURLsFailed(let primary, let fallback):
                let primaryFailure = classifyFailure(primary)
                let fallbackFailure = classifyFailure(fallback)
                if primaryFailure.disposition == .retryable &&
                    fallbackFailure.disposition == .retryable {
                    return ControlledActionOperationError(
                        reasonCode: "transport-error",
                        disposition: .retryable
                    )
                }
            default:
                break
            }
        }
        return ControlledActionOperationError(
            reasonCode: String(describing: type(of: error)),
            disposition: .nonRetryable
        )
    }

    private static func persistTerminal(
        _ request: ControlledActionRequest,
        _ actorRole: ControlledActionRole,
        _ result: ActionAuditRecord,
        _ attemptCount: Int,
        _ durableStore: any DurableActionQueueStore,
        _ now: @Sendable () -> Date
    ) async {
        await durableStore.upsert(DurableActionQueueEntry(
            request: request.sanitizedForPersistence(),
            actorRole: actorRole,
            state: result.state,
            attemptCount: attemptCount,
            reasonCode: result.reasonCode,
            updatedAt: now(),
            terminalRecord: result
        ))
    }

    private static func audit(
        request: ControlledActionRequest,
        actorRole: ControlledActionRole,
        state: ActionExecutionState,
        reasonCode: String,
        ledger: ControlledActionLedger,
        now: @Sendable () -> Date
    ) async -> ActionAuditRecord {
        let record = ActionAuditRecord(
            auditId: UUID(), requestId: request.id, providerRef: request.providerRef,
            action: request.action, targetRef: request.targetRef, risk: request.risk,
            actorRole: actorRole, idempotencyKey: request.idempotencyKey,
            state: state, reasonCode: reasonCode, recordedAt: now()
        )
        await ledger.append(record)
        return record
    }
}

private extension ControlledActionRequest {
    func sanitizedForPersistence() -> ControlledActionRequest {
        var copy = self
        copy.parameters = [:]
        return copy
    }

    func hasSameIdentity(as other: ControlledActionRequest) -> Bool {
        providerRef == other.providerRef && tenantRef == other.tenantRef &&
            action == other.action && targetRef == other.targetRef && risk == other.risk
    }
}
