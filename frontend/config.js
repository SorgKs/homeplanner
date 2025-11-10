/**
 * Global configuration for HomePlanner frontend.
 *
 * Здесь зафиксирован базовый адрес backend-сервера в локальной сети.
 * Для изменения адреса отредактируйте значения HOST и PORT ниже.
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
    const networkConfig = loadJson("network.json", { host: "localhost", port: 8000 });

    const major = projectConfig.major ?? 0;
    const minor = projectConfig.minor ?? 0;
    const FRONTEND_PATCH_VERSION = componentConfig.patch ?? 0;
    const FRONTEND_VERSION = `${major}.${minor}.${FRONTEND_PATCH_VERSION}`;
    const PROJECT_VERSION = `${major}.${minor}`;

    const HOST = networkConfig.host ?? "localhost";
    const PORT = Number(networkConfig.port ?? 8000);
    const API_VERSION_PATH = "/api/v0.2";
    const WS_PATH = `${API_VERSION_PATH}/tasks/stream`;
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

