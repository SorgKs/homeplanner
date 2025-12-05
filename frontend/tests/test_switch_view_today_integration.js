/**
 * Integration test for switchView('today') behavior.
 * 
 * This test analyzes the actual app.js code to verify that switchView('today')
 * triggers a data refresh from the server.
 * 
 * Run with: node frontend/tests/test_switch_view_today_integration.js
 */

const fs = require('fs');
const path = require('path');

function assert(condition, message) {
    if (!condition) {
        throw new Error(message);
    }
}

function test(name, fn) {
    try {
        fn();
        console.log(`✓ ${name}`);
        return true;
    } catch (error) {
        console.error(`✗ ${name}: ${error.message}`);
        return false;
    }
}

/**
 * Test that switchView('today') calls loadData() or getTodayIds().
 * 
 * This test analyzes the actual app.js source code to verify
 * that switchView('today') includes a call to refresh data.
 */
function testSwitchViewTodayCallsLoadData() {
    const appJsPath = path.join(__dirname, '..', 'app.js');
    const appJsContent = fs.readFileSync(appJsPath, 'utf-8');
    
    // Find switchView function
    const switchViewMatch = appJsContent.match(/function switchView\([^)]*\)\s*\{[\s\S]*?\n\s*if \(view === 'today'\)\s*\{[\s\S]*?\}/);
    
    assert(
        switchViewMatch !== null,
        'Could not find switchView function with "today" case'
    );
    
    const todayViewCode = switchViewMatch[0];
    
    // Check if switchView('today') calls loadData() or getTodayIds()
    const hasLoadData = todayViewCode.includes('loadData()') || todayViewCode.includes('loadData(');
    const hasGetTodayIds = todayViewCode.includes('getTodayIds()') || todayViewCode.includes('getTodayIds(');
    const hasApplyCurrentViewData = todayViewCode.includes('applyCurrentViewData()') || todayViewCode.includes('applyCurrentViewData(');
    
    // Expected: should call loadData() or getTodayIds() to refresh data
    // Current bug: only calls applyCurrentViewData() which uses cache
    if (hasLoadData || hasGetTodayIds) {
        console.log(`  → Found: switchView('today') calls ${hasLoadData ? 'loadData()' : 'getTodayIds()'} - correct behavior`);
        return true;
    } else if (hasApplyCurrentViewData) {
        console.log(`  → Found: switchView('today') only calls applyCurrentViewData() - this is the bug!`);
        console.log(`  → applyCurrentViewData() uses cache without refreshing from server`);
        throw new Error('switchView("today") does not refresh data from server - it only uses cache via applyCurrentViewData()');
    } else {
        throw new Error('switchView("today") does not call any data loading function');
    }
}

/**
 * Test that applyCurrentViewData() uses cache without API call.
 * 
 * This verifies the current buggy behavior.
 */
function testApplyCurrentViewDataUsesCache() {
    const appJsPath = path.join(__dirname, '..', 'app.js');
    const appJsContent = fs.readFileSync(appJsPath, 'utf-8');
    
    // Find applyCurrentViewData function
    const applyCurrentViewDataMatch = appJsContent.match(/function applyCurrentViewData\([^)]*\)\s*\{[\s\S]*?\n\s*if \(currentView === 'today'\)\s*\{[\s\S]*?\}/);
    
    assert(
        applyCurrentViewDataMatch !== null,
        'Could not find applyCurrentViewData function with "today" case'
    );
    
    const functionCode = applyCurrentViewDataMatch[0];
    
    // Check if it uses cache
    const usesCache = functionCode.includes('todayTasksCache') || functionCode.includes('todayTasksCache');
    
    // Check if it calls any API function
    const hasApiCall = functionCode.includes('getTodayIds') || 
                       functionCode.includes('loadData') ||
                       functionCode.includes('fetch') ||
                       functionCode.includes('tasksAPI');
    
    assert(
        usesCache,
        'applyCurrentViewData() should use todayTasksCache'
    );
    
    if (hasApiCall) {
        console.log(`  → Found: applyCurrentViewData() calls API - this would be correct, but it should be in switchView() instead`);
    } else {
        console.log(`  → Found: applyCurrentViewData() uses cache only (no API call) - this is the bug!`);
    }
    
    // This test documents the current buggy behavior
    return true;
}

// Run tests
console.log('Running integration tests for switchView("today") behavior...\n');
console.log('These tests analyze the actual app.js source code.\n');

let passed = 0;
let failed = 0;

if (test('switchView("today") should call loadData() or getTodayIds()', testSwitchViewTodayCallsLoadData)) {
    passed++;
} else {
    failed++;
}

if (test('applyCurrentViewData() uses cache without API call (current bug)', testApplyCurrentViewDataUsesCache)) {
    passed++;
} else {
    failed++;
}

console.log(`\nTests: ${passed + failed} total, ${passed} passed, ${failed} failed`);

if (failed > 0) {
    console.log('\n⚠️  Test failed! This indicates the bug: switchView("today") does not refresh data from server.');
    console.log('Expected: switchView("today") should call loadData() or getTodayIds() to refresh data.');
    console.log('Current: switchView("today") only calls applyCurrentViewData() which uses stale cache.');
    process.exit(1);
} else {
    console.log('\n✓ All tests passed. Code analysis verified expected behavior.');
}

