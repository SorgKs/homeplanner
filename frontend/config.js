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
        console.error("Failed to load required settings.toml", error);
        throw new Error("Не удалось загрузить common/config/settings.toml для frontend");
    }

    const major = projectConfig.major ?? 0;
    const minor = projectConfig.minor ?? 0;
    const FRONTEND_PATCH_VERSION = componentConfig.patch ?? 0;
    const FRONTEND_VERSION = `${major}.${minor}.${FRONTEND_PATCH_VERSION}`;
    const PROJECT_VERSION = `${major}.${minor}`;

    const apiConfig = settings.api;
    const websocketConfig = settings.websocket;

    if (!apiConfig || typeof apiConfig.version !== "string") {
        throw new Error(
            "Отсутствует обязательная настройка [api].version в common/config/settings.toml",
        );
    }
    if (!websocketConfig || typeof websocketConfig.tasks_stream_path !== "string") {
        throw new Error(
            "Отсутствует обязательная настройка [websocket].tasks_stream_path в common/config/settings.toml",
        );
    }

    const apiVersion = apiConfig.version;
    const API_VERSION_PATH = `/api/v${apiVersion}`;

    /**
     * Определяем хост и порт backend по фактическому origin браузера.
     *
     * Важно:
     * - фронтенд всегда обращается к API по тому же host/port, откуда был загружен
     *   (window.location), то есть JS никогда не берёт IP/порт backend из settings.toml;
     * - файл настроек влияет только на версию API (/api/vX) и путь WebSocket
     *   (секции [api] и [websocket]), но не на IP-адрес для HTTP-запросов.
     */
    if (typeof window === "undefined" || !window.location) {
        throw new Error("frontend/config.js должен выполняться в браузере (window.location)");
    }

    const { protocol, hostname, port: locationPort } = window.location;

    if (!protocol || !hostname) {
        throw new Error("Невозможно определить origin из window.location");
    }

    const PROTOCOL = protocol;
    const HOST = hostname;

    let PORT;
    if (locationPort) {
        // Порт явно указан в URL (например, :8000)
        const numericPort = Number(locationPort);
        if (Number.isNaN(numericPort)) {
            throw new Error(`Некорректный порт в window.location: "${locationPort}"`);
        }
        PORT = numericPort;
    } else if (protocol === "https:") {
        // Стандартный порт для HTTPS
        PORT = 443;
    } else if (protocol === "http:") {
        // Стандартный порт для HTTP
        PORT = 80;
    } else {
        throw new Error(
            `Неизвестный протокол "${protocol}" без явного порта в window.location; ` +
                "укажите порт в URL явно",
        );
    }

    const wsSuffixRaw = websocketConfig.tasks_stream_path;
    const wsSuffix = wsSuffixRaw.startsWith("/") ? wsSuffixRaw : `/${wsSuffixRaw}`;
    const WS_PATH = `${API_VERSION_PATH}${wsSuffix}`;
    const API_BASE_URL = `${PROTOCOL}//${HOST}:${PORT}${API_VERSION_PATH}`;

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

