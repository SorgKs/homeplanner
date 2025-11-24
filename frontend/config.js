/**
 * Global configuration for HomePlanner frontend.
 *
 * Настройки загружаются из единого файла common/config/settings.toml.
 * Шаблон: common/config/settings.toml.template
 * Изменяйте параметры сервера/фронтенда в соответствующих секциях settings.toml.
 */
(function configureHomePlanner() {
    function loadJson(path, fallback) {
        try {
            const xhr = new XMLHttpRequest();
            xhr.open("GET", path, false);
            xhr.overrideMimeType("application/json");
            xhr.send(null);
            if (xhr.status >= 200 && xhr.status < 300 && xhr.responseText) {
                return JSON.parse(xhr.responseText);
            }
        } catch (error) {
            console.warn(`Failed to load ${path}`, error);
        }
        return fallback;
    }

    const projectConfig = loadJson("version.project.json", { major: 0, minor: 0 });
    const componentConfig = loadJson("version.json", { patch: 0 });
    
    function loadText(path) {
        const xhr = new XMLHttpRequest();
        xhr.open("GET", path, false);
        xhr.overrideMimeType("text/plain");
        xhr.send(null);
        if (xhr.status >= 200 && xhr.status < 300 && xhr.responseText) {
            return xhr.responseText;
        }
        throw new Error(`Failed to load ${path}`);
    }

    function parseToml(content) {
        const result = {};
        let currentSection = null;
        const lines = content.split(/\r?\n/);
        for (let i = 0; i < lines.length; i += 1) {
            let line = lines[i].trim();
            if (!line || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.slice(1, -1).trim();
                if (currentSection) {
                    result[currentSection] = result[currentSection] || {};
                }
                continue;
            }
            if (!currentSection || line.startsWith("[") || !line.includes("=")) {
                continue;
            }
            const [rawKey, ...rawValueParts] = line.split("=");
            let valueRaw = rawValueParts.join("=").trim();
            if (!rawKey || !valueRaw) {
                continue;
            }
            const key = rawKey.trim();
            const commentIndex = valueRaw.indexOf("#");
            if (commentIndex !== -1) {
                valueRaw = valueRaw.slice(0, commentIndex).trim();
            }
            if (!valueRaw) {
                continue;
            }
            if (valueRaw.startsWith("[") && !valueRaw.endsWith("]")) {
                while (i + 1 < lines.length && !valueRaw.trim().endsWith("]")) {
                    i += 1;
                    valueRaw += lines[i].split("#")[0];
                }
            }
            result[currentSection][key] = parseTomlValue(valueRaw.trim());
        }
        return result;
    }

    function parseTomlValue(rawValue) {
        if (!rawValue) return null;
        if (rawValue.startsWith("[") && rawValue.endsWith("]")) {
            const inner = rawValue.slice(1, -1).trim();
            if (!inner) {
                return [];
            }
            return inner
                .split(",")
                .map((item) => item.trim())
                .filter(Boolean)
                .map((item) => parseTomlValue(item));
        }
        if (
            (rawValue.startsWith('"') && rawValue.endsWith('"')) ||
            (rawValue.startsWith("'") && rawValue.endsWith("'"))
        ) {
            return rawValue.slice(1, -1);
        }
        if (rawValue === "true" || rawValue === "false") {
            return rawValue === "true";
        }
        const numeric = Number(rawValue);
        return Number.isNaN(numeric) ? rawValue : numeric;
    }

    let settings = {};
    try {
        const tomlContent = loadText("/common/config/settings.toml");
        settings = parseToml(tomlContent);
    } catch (error) {
        console.warn("Failed to load settings.toml, using fallbacks", error);
    }

    const major = projectConfig.major ?? 0;
    const minor = projectConfig.minor ?? 0;
    const FRONTEND_PATCH_VERSION = componentConfig.patch ?? 0;
    const FRONTEND_VERSION = `${major}.${minor}.${FRONTEND_PATCH_VERSION}`;
    const PROJECT_VERSION = `${major}.${minor}`;

    const serverConfig = settings.server ?? {};
    const networkConfig = settings.network ?? {};
    const frontendConfig = settings.frontend ?? {};
    const apiConfig = settings.api ?? {};
    const websocketConfig = settings.websocket ?? {};

    const apiVersion = typeof apiConfig.version === "string" ? apiConfig.version : "0.2";
    const API_VERSION_PATH = `/api/v${apiVersion}`;

    const hostCandidate =
        frontendConfig.host ??
        networkConfig.host ??
        (serverConfig.host === "0.0.0.0" ? "localhost" : serverConfig.host);
    const HOST = hostCandidate || "localhost";

    const portCandidate = Number(
        frontendConfig.port ?? networkConfig.port ?? serverConfig.port ?? 8000,
    );
    const PORT = Number.isNaN(portCandidate) ? 8000 : portCandidate;

    const wsSuffixRaw =
        typeof websocketConfig.tasks_stream_path === "string"
            ? websocketConfig.tasks_stream_path
            : "/tasks/stream";
    const wsSuffix = wsSuffixRaw.startsWith("/") ? wsSuffixRaw : `/${wsSuffixRaw}`;
    const WS_PATH = `${API_VERSION_PATH}${wsSuffix}`;
    const API_BASE_URL = `http://${HOST}:${PORT}${API_VERSION_PATH}`;

    window.HP_BACKEND_HOST = HOST;
    window.HP_BACKEND_PORT = PORT;
    window.HP_API_BASE_URL = API_BASE_URL;
    window.HP_WS_PATH = WS_PATH;
    window.HP_FRONTEND_PATCH_VERSION = FRONTEND_PATCH_VERSION;
    window.HP_PROJECT_MAJOR = major;
    window.HP_PROJECT_MINOR = minor;
    window.HP_PROJECT_VERSION = PROJECT_VERSION;
    window.HP_FRONTEND_VERSION = FRONTEND_VERSION;
    window.HP_VERSION_CONFIG = projectConfig;
    window.HP_COMPONENT_VERSION_CONFIG = componentConfig;
    window.API_BASE_URL = API_BASE_URL;
})();

