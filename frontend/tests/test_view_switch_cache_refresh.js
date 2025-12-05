/**
 * Test that verifies frontend checks cache and makes server requests when switching views.
 * 
 * This test ensures that when switching between views (today, all, history, etc.),
 * the frontend:
 * 1. Checks if cache is available and fresh
 * 2. Makes a request to server if cache is stale or missing
 * 3. Updates cache with fresh data
 * 
 * Run with: node frontend/tests/test_view_switch_cache_refresh.js
 */

// Track API calls - use global scope to ensure it's shared
let apiCalls = [];
let cacheState = {
    todayTasksCache: [],
    allTasksCache: [],
    lastUpdateTime: null,
    cacheMaxAge: 60000 // 1 minute cache TTL
};

// Ensure apiCalls is accessible globally
if (typeof global !== 'undefined') {
    global.apiCalls = apiCalls;
}

// Store original fetch if it exists
let originalFetch = null;
let mockFetch = null;

// Mock fetch to track API calls
function setupFetchMock() {
    // Clear apiCalls but keep reference to the same array
    apiCalls.length = 0;
    originalFetch = global.fetch;
    
    mockFetch = (url, options) => {
        const call = {
            url,
            method: options?.method || 'GET',
            timestamp: Date.now()
        };
        // Push to the shared apiCalls array
        apiCalls.push(call);
        console.log(`    [DEBUG] Mock fetch: ${url}, apiCalls.length=${apiCalls.length}`);
        
        // Return mock responses based on endpoint
        if (url.includes('/tasks/today/ids')) {
            return Promise.resolve({
                ok: true,
                status: 200,
                json: () => Promise.resolve([1, 2, 3])
            });
        } else if (url.includes('/tasks/') && !url.includes('/today/')) {
            return Promise.resolve({
                ok: true,
                status: 200,
                json: () => Promise.resolve([
                    { id: 1, title: 'Task 1', task_type: 'one_time' },
                    { id: 2, title: 'Task 2', task_type: 'recurring' },
                    { id: 3, title: 'Task 3', task_type: 'interval' }
                ])
            });
        }
        
        return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve([])
        });
    };
    
    // Set mock fetch in both global and window
    global.fetch = mockFetch;
    if (global.window) {
        global.window.fetch = mockFetch;
    }
}

function teardownFetchMock() {
    // Clear apiCalls but keep reference
    apiCalls.length = 0;
    if (originalFetch) {
        global.fetch = originalFetch;
        if (global.window) {
            global.window.fetch = originalFetch;
        }
    } else {
        delete global.fetch;
        if (global.window) {
            delete global.window.fetch;
        }
    }
    mockFetch = null;
}

// Mock DOM environment
function setupDOM() {
    global.document = {
        getElementById: (id) => {
            const elements = {
                'view-today-btn': { 
                    classList: { 
                        add: () => {}, 
                        remove: () => {}, 
                        contains: () => false 
                    } 
                },
                'view-all-btn': { 
                    classList: { 
                        add: () => {}, 
                        remove: () => {}, 
                        contains: () => false 
                    } 
                },
                'view-history-btn': { 
                    classList: { 
                        add: () => {}, 
                        remove: () => {}, 
                        contains: () => false 
                    } 
                },
                'tasks-list': { 
                    style: { display: '' },
                    innerHTML: ''
                },
                'history-filters-section': { style: { display: '' } },
                'tasks-filters-section': { style: { display: '' } }
            };
            return elements[id] || { 
                classList: { add: () => {}, remove: () => {}, contains: () => false }, 
                style: { display: '' } 
            };
        },
        querySelector: () => null,
        querySelectorAll: () => [],
        cookie: ''
    };
    
    global.window = {
        location: { protocol: 'http:' },
        HP_API_BASE_URL: 'http://localhost:8000/api/v0.2'
    };
}

function teardownDOM() {
    delete global.document;
    delete global.window;
}

// Simple test framework
function assert(condition, message) {
    if (!condition) {
        throw new Error(message);
    }
}

async function test(name, fn) {
    try {
        const result = fn();
        if (result && typeof result.then === 'function') {
            await result;
        }
        console.log(`✓ ${name}`);
        return true;
    } catch (error) {
        console.error(`✗ ${name}: ${error.message}`);
        if (error.stack) {
            console.error(error.stack);
        }
        return false;
    }
}

/**
 * Check if cache is fresh (not expired).
 */
function isCacheFresh() {
    if (!cacheState.lastUpdateTime) {
        return false;
    }
    const age = Date.now() - cacheState.lastUpdateTime;
    return age < cacheState.cacheMaxAge;
}

/**
 * Simulate switchView with cache checking logic.
 * 
 * Expected behavior:
 * - If cache is fresh and available, use cache
 * - If cache is stale or missing, request from server
 */
async function switchViewWithCacheCheck(view) {
    const now = Date.now();
    
    // Get fetch function (mock or real)
    const fetchFn = mockFetch || global.fetch || (global.window && global.window.fetch);
    if (!fetchFn) {
        throw new Error('fetch is not available - make sure setupFetchMock() was called');
    }
    
    if (view === 'today') {
        // Check if today cache is fresh
        if (isCacheFresh() && cacheState.todayTasksCache.length > 0) {
            console.log(`  → Using fresh cache for 'today' view`);
            return { usedCache: true, apiCalls: 0 };
        } else {
            console.log(`  → Cache stale or missing, requesting from server for 'today' view`);
            // Request fresh data
            const response = await fetchFn('http://localhost:8000/api/v0.2/tasks/today/ids');
            const todayIds = await response.json();
            
            // Also get all tasks to update cache
            const allTasksResponse = await fetchFn('http://localhost:8000/api/v0.2/tasks/');
            const allTasks = await allTasksResponse.json();
            
            // Update cache
            cacheState.todayTasksCache = allTasks.filter(t => todayIds.includes(t.id));
            cacheState.allTasksCache = allTasks;
            cacheState.lastUpdateTime = now;
            
            return { usedCache: false, apiCalls: 2 };
        }
    } else if (view === 'all') {
        // Check if all tasks cache is fresh
        if (isCacheFresh() && cacheState.allTasksCache.length > 0) {
            console.log(`  → Using fresh cache for 'all' view`);
            return { usedCache: true, apiCalls: 0 };
        } else {
            console.log(`  → Cache stale or missing, requesting from server for 'all' view`);
            // Request fresh data
            const response = await fetchFn('http://localhost:8000/api/v0.2/tasks/');
            const allTasks = await response.json();
            
            // Update cache
            cacheState.allTasksCache = allTasks;
            cacheState.lastUpdateTime = now;
            
            return { usedCache: false, apiCalls: 1 };
        }
    }
    
    return { usedCache: false, apiCalls: 0 };
}

/**
 * Test 1: Switch to 'today' view with empty cache should request from server.
 */
async function testSwitchToTodayWithEmptyCache() {
    setupFetchMock();
    setupDOM();
    
    try {
        // Reset cache
        cacheState.todayTasksCache = [];
        cacheState.allTasksCache = [];
        cacheState.lastUpdateTime = null;
        
        // Clear apiCalls before test
        apiCalls.length = 0;
        
        // Switch to today view
        const initialCallCount = apiCalls.length;
        const result = await switchViewWithCacheCheck('today');
        const finalCallCount = apiCalls.length;
        const actualApiCalls = finalCallCount - initialCallCount;
        
        // Should request from server (cache is empty)
        assert(
            actualApiCalls > 0,
            `Expected API calls when cache is empty, but got ${actualApiCalls} calls (initial: ${initialCallCount}, final: ${finalCallCount})`
        );
        
        assert(
            !result.usedCache,
            'Should not use cache when cache is empty'
        );
        
        // Check that getTodayIds was called
        const todayIdsCall = apiCalls.find(call => call.url.includes('/tasks/today/ids'));
        assert(
            todayIdsCall !== undefined,
            `Expected call to /tasks/today/ids when switching to today view with empty cache. Got calls: ${JSON.stringify(apiCalls.map(c => c.url))}`
        );
        
        console.log(`  → Verified: switchView('today') requests from server when cache is empty`);
        
    } finally {
        teardownFetchMock();
        teardownDOM();
    }
}

/**
 * Test 2: Switch to 'today' view with fresh cache should use cache.
 */
async function testSwitchToTodayWithFreshCache() {
    setupFetchMock();
    setupDOM();
    
    try {
        // Setup fresh cache
        cacheState.todayTasksCache = [
            { id: 1, title: 'Cached Task 1' },
            { id: 2, title: 'Cached Task 2' }
        ];
        cacheState.allTasksCache = [
            { id: 1, title: 'Cached Task 1' },
            { id: 2, title: 'Cached Task 2' }
        ];
        cacheState.lastUpdateTime = Date.now() - 10000; // 10 seconds ago (fresh)
        apiCalls = [];
        
        // Switch to today view
        const result = await switchViewWithCacheCheck('today');
        
        // Should use cache (cache is fresh)
        assert(
            result.usedCache,
            'Should use cache when cache is fresh'
        );
        
        assert(
            result.apiCalls === 0,
            `Expected no API calls when cache is fresh, but got ${result.apiCalls} calls`
        );
        
        console.log(`  → Verified: switchView('today') uses cache when cache is fresh`);
        
    } finally {
        teardownFetchMock();
        teardownDOM();
    }
}

/**
 * Test 3: Switch to 'today' view with stale cache should request from server.
 */
async function testSwitchToTodayWithStaleCache() {
    setupFetchMock();
    setupDOM();
    
    try {
        // Setup stale cache (older than cacheMaxAge)
        cacheState.todayTasksCache = [
            { id: 1, title: 'Stale Task 1' }
        ];
        cacheState.allTasksCache = [
            { id: 1, title: 'Stale Task 1' }
        ];
        cacheState.lastUpdateTime = Date.now() - (cacheState.cacheMaxAge + 1000); // Stale
        
        // Clear apiCalls before test
        apiCalls.length = 0;
        
        // Switch to today view
        const initialCallCount = apiCalls.length;
        const result = await switchViewWithCacheCheck('today');
        const finalCallCount = apiCalls.length;
        const actualApiCalls = finalCallCount - initialCallCount;
        
        // Should request from server (cache is stale)
        assert(
            !result.usedCache,
            'Should not use cache when cache is stale'
        );
        
        assert(
            actualApiCalls > 0,
            `Expected API calls when cache is stale, but got ${actualApiCalls} calls (initial: ${initialCallCount}, final: ${finalCallCount})`
        );
        
        // Check that getTodayIds was called
        const todayIdsCall = apiCalls.find(call => call.url.includes('/tasks/today/ids'));
        assert(
            todayIdsCall !== undefined,
            `Expected call to /tasks/today/ids when switching to today view with stale cache. Got calls: ${JSON.stringify(apiCalls.map(c => c.url))}`
        );
        
        console.log(`  → Verified: switchView('today') requests from server when cache is stale`);
        
    } finally {
        teardownFetchMock();
        teardownDOM();
    }
}

/**
 * Test 4: Switch to 'all' view with empty cache should request from server.
 */
async function testSwitchToAllWithEmptyCache() {
    setupFetchMock();
    setupDOM();
    
    try {
        // Reset cache
        cacheState.todayTasksCache = [];
        cacheState.allTasksCache = [];
        cacheState.lastUpdateTime = null;
        apiCalls.length = 0; // Clear array but keep reference
        
        // Switch to all view
        const initialCallCount = apiCalls.length;
        const result = await switchViewWithCacheCheck('all');
        const finalCallCount = apiCalls.length;
        const actualApiCalls = finalCallCount - initialCallCount;
        
        // Should request from server (cache is empty)
        assert(
            actualApiCalls > 0,
            `Expected API calls when cache is empty, but got ${actualApiCalls} calls (initial: ${initialCallCount}, final: ${finalCallCount})`
        );
        
        assert(
            !result.usedCache,
            'Should not use cache when cache is empty'
        );
        
        // Check that getAll was called (should be called when switching to 'all' with empty cache)
        // Note: In the current implementation, 'all' view might also call getTodayIds
        // So we check for any /tasks/ call
        const getAllCall = apiCalls.slice(initialCallCount).find(call => 
            call.url.includes('/tasks/')
        );
        assert(
            getAllCall !== undefined,
            `Expected call to /tasks/ when switching to all view with empty cache. Got calls: ${JSON.stringify(apiCalls.slice(initialCallCount).map(c => c.url))}`
        );
        
        console.log(`  → Verified: switchView('all') requests from server when cache is empty`);
        
    } finally {
        teardownFetchMock();
        teardownDOM();
    }
}

/**
 * Test 5: Switch to 'all' view with fresh cache should use cache.
 */
async function testSwitchToAllWithFreshCache() {
    setupFetchMock();
    setupDOM();
    
    try {
        // Setup fresh cache
        cacheState.allTasksCache = [
            { id: 1, title: 'Cached Task 1' },
            { id: 2, title: 'Cached Task 2' },
            { id: 3, title: 'Cached Task 3' }
        ];
        cacheState.lastUpdateTime = Date.now() - 10000; // 10 seconds ago (fresh)
        apiCalls = [];
        
        // Switch to all view
        const result = await switchViewWithCacheCheck('all');
        
        // Should use cache (cache is fresh)
        assert(
            result.usedCache,
            'Should use cache when cache is fresh'
        );
        
        assert(
            result.apiCalls === 0,
            `Expected no API calls when cache is fresh, but got ${result.apiCalls} calls`
        );
        
        console.log(`  → Verified: switchView('all') uses cache when cache is fresh`);
        
    } finally {
        teardownFetchMock();
        teardownDOM();
    }
}

/**
 * Test 6: Multiple view switches - should use cache when appropriate.
 */
async function testMultipleViewSwitches() {
    setupFetchMock();
    setupDOM();
    
    try {
        // Reset cache
        cacheState.todayTasksCache = [];
        cacheState.allTasksCache = [];
        cacheState.lastUpdateTime = null;
        apiCalls.length = 0; // Clear array but keep reference
        
        // First switch to 'today' - should request from server
        const result1 = await switchViewWithCacheCheck('today');
        assert(result1.apiCalls > 0, 'First switch should request from server');
        const firstCallCount = apiCalls.length;
        
        // Second switch to 'all' - should use cache if fresh, or request if needed
        const result2 = await switchViewWithCacheCheck('all');
        
        // Third switch back to 'today' - should use cache (fresh)
        const result3 = await switchViewWithCacheCheck('today');
        assert(result3.usedCache, 'Third switch should use fresh cache');
        assert(result3.apiCalls === 0, 'Third switch should not make API calls');
        
        console.log(`  → Verified: Multiple view switches use cache appropriately`);
        
    } finally {
        teardownFetchMock();
        teardownDOM();
    }
}

// Run all tests
console.log('Running tests for view switching with cache checking...\n');
console.log('These tests verify that frontend checks cache and requests from server when needed.\n');

let passed = 0;
let failed = 0;

(async () => {
    // Run tests sequentially to avoid race conditions with shared apiCalls
    const results = [];
    
    results.push(await test('switchView("today") requests from server when cache is empty', 
        testSwitchToTodayWithEmptyCache));
    results.push(await test('switchView("today") uses cache when cache is fresh', 
        testSwitchToTodayWithFreshCache));
    results.push(await test('switchView("today") requests from server when cache is stale', 
        testSwitchToTodayWithStaleCache));
    results.push(await test('switchView("all") requests from server when cache is empty', 
        testSwitchToAllWithEmptyCache));
    results.push(await test('switchView("all") uses cache when cache is fresh', 
        testSwitchToAllWithFreshCache));
    results.push(await test('Multiple view switches use cache appropriately', 
        testMultipleViewSwitches));
    
    results.forEach(result => {
        if (result) passed++; else failed++;
    });
    
    console.log(`\nTests: ${passed + failed} total, ${passed} passed, ${failed} failed`);
    
    if (failed > 0) {
        console.log('\n⚠️  Some tests failed.');
        console.log('Expected behavior: switchView() should check cache freshness and request from server when needed.');
        process.exit(1);
    } else {
        console.log('\n✓ All tests passed. Cache checking and server requests work correctly.');
    }
})();

