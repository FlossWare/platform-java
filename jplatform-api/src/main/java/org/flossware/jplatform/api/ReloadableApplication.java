/*
 * Copyright (C) 2024-2026 FlossWare
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.flossware.jplatform.api;

import java.util.Map;

/**
 * Extended application interface supporting hot code reload.
 *
 * <p>Applications implementing this interface can be reloaded with new code
 * without full platform restart. The platform manages classloader hot-swapping
 * and provides mechanisms for state preservation across reloads.</p>
 *
 * <p>Reload lifecycle:</p>
 * <ol>
 *   <li>{@link #beforeReload()} - Save application state</li>
 *   <li>Platform stops old application instance</li>
 *   <li>Platform swaps to new classloader with updated JARs</li>
 *   <li>Platform creates new application instance</li>
 *   <li>{@link #afterReload(ApplicationContext, Map)} - Restore state</li>
 * </ol>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * public class MyApp implements ReloadableApplication {
 *     private volatile Map<String, Object> appState = new ConcurrentHashMap<>();
 *
 *     @Override
 *     public void start(ApplicationContext context) throws Exception {
 *         // Normal startup logic
 *         appState.put("started", System.currentTimeMillis());
 *     }
 *
 *     @Override
 *     public void stop() throws Exception {
 *         // Normal shutdown logic
 *     }
 *
 *     @Override
 *     public Map<String, Object> beforeReload() {
 *         // Capture state to preserve across reload
 *         Map<String, Object> state = new HashMap<>();
 *         state.put("appState", new HashMap<>(appState));
 *         state.put("reloadTime", System.currentTimeMillis());
 *         return state;
 *     }
 *
 *     @Override
 *     public void afterReload(ApplicationContext context, Map<String, Object> savedState) {
 *         // Restore state from previous version
 *         if (savedState != null && savedState.containsKey("appState")) {
 *             this.appState = new ConcurrentHashMap<>((Map) savedState.get("appState"));
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 2.0
 */
public interface ReloadableApplication extends Application {

    /**
     * Called before the application is reloaded with new code.
     *
     * <p>This method should capture any state that needs to be preserved across
     * the reload. The returned map will be passed to {@link #afterReload} in the
     * new application instance.</p>
     *
     * <p>State preservation guidelines:</p>
     * <ul>
     *   <li>Only serialize primitive types, Strings, and simple data structures</li>
     *   <li>Avoid storing references to application classes (they won't exist after reload)</li>
     *   <li>Keep state size reasonable (large state slows reload)</li>
     *   <li>Return empty map if no state needs preserving</li>
     * </ul>
     *
     * @return map of state to preserve (keys are arbitrary strings, values should be serializable)
     * @throws Exception if state capture fails
     */
    Map<String, Object> beforeReload() throws Exception;

    /**
     * Called after the application has been reloaded with new code.
     *
     * <p>This method receives the state captured by {@link #beforeReload()} from the
     * previous version and the new application context. Use this to restore any
     * necessary state.</p>
     *
     * <p>Note that this is called in the NEW classloader with the NEW code. The saved
     * state comes from the OLD version, so care must be taken with version compatibility.</p>
     *
     * @param context the new application context (same ID but new classloader)
     * @param savedState the state returned by beforeReload() in the previous version (may be null)
     * @throws Exception if state restoration fails
     */
    void afterReload(ApplicationContext context, Map<String, Object> savedState) throws Exception;
}
