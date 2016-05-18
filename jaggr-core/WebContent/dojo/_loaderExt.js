/*
 * (C) Copyright 2012, IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *  AMD combo loader extention for the Dojo AMD loader.
 *  <p>
 *  This file is combined with loaderExtCommon.js, and with dynamically 
 *  injected javascript code, by the Dojo HttpTransport extension on the
 *  aggregator when the combo/loaderExt.js pseudo resource is 
 *  requested.
 */
(function() {
var depmap = {},
    deps = [],
    userConfig = (function(){
    	// make sure we're looking at global dojoConfig etc.
    	return this.dojoConfig || this.djConfig || this.require;
    })(),

    combo = userConfig.combo,
    
    // The context path of the aggregator service
    contextPath = combo.contextPath,

    // ( 4k/4096 with a buffer just in case - 96) 
    // Set to 0 to disable url length checks.
    maxUrlLength = (typeof(combo.maxUrlLength) === 'undefined') ?
    		// IE doesn't cache responses for request URLs greater than about 2K
    		(/MSIE (\d+\.\d+);/.test(navigator.userAgent) ? 2000 : 4000) :
    		combo.maxUrlLength,

    // The following vars are referenced by javascript code injected by the 
    // server.
    plugins = (combo.plugins = combo.plugins || {}),
    aliases = (userConfig.aliases = userConfig.aliases || []),
    // Flag indicating whether or not has features sent to the server should
    // include features that evaluate to undefined
    includeUndefinedFeatures,

    // Map of module name to number id pairs
    moduleIdMap = {},
    
    // cumulative exclude modules
    excludes = [],
    
    // The current key into the pendingLoads map.  Non-null when defining modules between
    // combo.beginDefs() and combo.endDefs() invocations.
    currentLoadKey = null,
    
    // Property map of pending loads.  The keys are the stringified mid list that was provided
    // to the combo.done() load callback.  The values are {mids:xxx, defs:xxx} objects where
    // mids is the same module list use to make the key and defs is the array of define() 
    // function call arguments.  There is a one-to-one correspondence between the elements
    // in mids and the elements in defs.
    pendingLoads = {},
    
    // Array of keys for entries in pendingLoads, in load request order (i.e. the order in 
    // which the corresponding combo.done() load callback was invoked).
    pendingLoadKeys = [],
    
    // The original global define() function before we re-mapped it to our comboDefine function.
    originalDefine,
    
    // Query arg from window.location
    windowArgs = parseQueryArgs(window.location.search) || {};
    
    // Query arg from script tag used to load this code
    scriptArgs = combo.scriptId && parseQueryArgs((document.getElementById(combo.scriptId)||{}).src) || {},
    
    comboDefine = function() {
    	if (currentLoadKey) {
    		pendingLoads[currentLoadKey].defArgs.push(arguments);
    	} else {
    		originalDefine.apply(this, arguments);
    	}
    };
    

// Copy config params from the combo config property
for (var s in params) {
	if (typeof combo[s] !== 'undefined') {
		params[s][0] = combo[s];
	}
}

extraArgs = combo.extraArgs || {};
userConfig.has = userConfig.has || {};

//By default, don't include config- features since the loader uses the has map as a dumping 
//ground for anything specified in the config.
featureFilter = combo.featureFilter || function(name) { return !/^config-/.test(name);};

// Set this so that the loader won't synchronously call require.callback
userConfig.has["dojo-built"] = true;

userConfig.async = true;		// use async loader

//Enable the combo api
userConfig.has['dojo-combo-api'] = 1;

/**
 * url processor for handling cache bust query arg
 */
urlProcessors.push(function(url) {
	cb = windowArgs.cachebust ||
	     scriptArgs.cachebust || scriptArgs.cb ||
	     combo.cacheBust || dojo.config.cacheBust;
	if (cb) {
		url += ('&cb=' + cb);
	}
	return url;
});

/**
 * urlProcessor to add query locale query arg
 */
urlProcessors.push(function(url, deps){
	// determine if any of the modules in the request are i18n resources
	var isI18n = true;
	if (!combo.serverExpandLayers) {
		isI18n = false;
		for (var i = 0; i < deps.length; i++) {
			if (combo.isI18nResource(deps[i])) {
				isI18n = true;
				break;
			}
		}
	}
	if (isI18n) {
		// If request contains i18n resources, then add the locales to the request URL
		if (typeof dojo !== 'undefined' && dojo.locale && !/[?&]locs=/.test(url)) {
			url+=('&locs='+[dojo.locale].concat(dojo.config.extraLocale || []).join(','));
		}
	}
	return url;
});

// require.combo needs to be defined before this code is loaded
combo.done = function(load, config, opt_deps) {
	var hasArg = "", base64,
	    sendRequest = function(load, config, opt_deps) {
			var mids = [], i, dep;
			opt_deps = opt_deps || deps;
			
			// Determine if we need to split the request into i18n/non-i18n parts
			if (combo.i18nSplit && !combo.serverExpandLayers) {
				var i18nModules = [], nonI18nModules = [];
				for (i = 0; i < opt_deps.length; i++) {
					dep = opt_deps[i];
					(combo.isI18nResource(dep) ? i18nModules : nonI18nModules).push(dep);
				}
				if (i18nModules.length && nonI18nModules.length) {
					// Mixed request.  Separate into i18n and non-i18n requests
					deps = [];
					depmap = {};
					sendRequest(load, config, nonI18nModules);
					sendRequest(load, config, i18nModules);
					return;
				}
			}
			
			for (i = 0, dep; !!(dep = opt_deps[i]); i++) {
				mids[i] = dep.prefix ? (dep.prefix + "!" + dep.name) : dep.name;
			}
			
			var url = contextPath || "";
			url = addModulesToUrl(url, ["modules", "moduleIds"], opt_deps, moduleIdMap, base64 ? base64.encode : null);
			url = addModulesToUrl(url, ["exEnc", "exIds"], excludes || [], moduleIdMap,  base64 ? base64.encode : null);
			url += (hasArg ? '&' + hasArg : "");
			
			// Allow any externally provided URL processors to make their contribution
			// to the URL
			for (i = 0; i < urlProcessors.length; i++) {
				url = urlProcessors[i](url, opt_deps);
			}
			
			if (config.has("dojo-trace-api")) {
				config.trace("loader-inject-combo", [mids.join(', ')]);
			}
			if (maxUrlLength && url.length > maxUrlLength) {
				var parta = opt_deps.slice(0, opt_deps.length/2),
				    partb = opt_deps.slice(opt_deps.length/2, opt_deps.length);
				deps = [];
				depmap = {};
				sendRequest(load, config, parta);
				sendRequest(load, config, partb);
			} else {
				if (combo.serverExpandLayers) {
					excludes = excludes.concat(deps);
					// Create pending load entries for this load request so that we can manage the order in 
					// which the modules are defined independent of the order in which the responses arrive.
					var key = mids.join(',');
					pendingLoadKeys.push(key);
					pendingLoads[key] = {mids:mids, defs:null};
				}
				if (deps === opt_deps) {
					// we have not split the module list to trim url size, so we can clear this safely.
					// otherwise clearing these is the responsibility of the initial function.
					deps = [];
					depmap = {};
				}
				load(mids, url);
			}
	    };
	    
	if (!originalDefine) {
		// Replace the global define() function with our own and save the original
		(function() {
			originalDefine = this.define;
			this.define = comboDefine;
		})();
	}
	
	// Get base64 decoder
	try {
		base64 = require('dojox/encoding/base64');
	} catch (ignore) {}

	if (typeof includeUndefinedFeatures == 'undefined') {
		// Test to determine if we can include features that evaluate to undefined.
		// If simply querying a feature puts the feature in the cache, then we
		// can't send features that evaluate to undefined to the server.
		// (Note: this behavior exists in early versions of dojo 1.7)
		var test_feature = 'combo-test-for-undefined';
		config.has(test_feature);
		includeUndefinedFeatures = !(test_feature in config.has.cache);
	}
	hasArg = computeHasArg(config.has, config.has.cache, includeUndefinedFeatures);
	
	// If sending the feature set in a cookie is enabled, then try to 
	// set the cookie.
	var featureMap = null, featureCookie = null;
	if (!!(featureMap = config.has("combo-feature-map"))) {
		hasArg = featureMap.getQueryString(hasArg);
	} else if (!!(featureCookie = config.has("combo-feature-cookie"))) {
		hasArg = featureCookie.setCookie(hasArg, contextPath);
	}

	sendRequest(load, config, opt_deps);
};

combo.add = function (prefix, name, url, config) {
	if (config.cache[name] || !combo.isSupportedModule(name, url)) {
		return false;
	}
	if (!depmap[name] && (!prefix || prefix in plugins)) {
		deps.push(depmap[name] = {
			prefix: prefix,
			name: name
		});
	}
	
	var canHandle = !!depmap[name];
	if (!canHandle && config.has("dojo-trace-api")) {
		config.trace("loader-inject-combo-reject", ["can't handle: " + prefix + "!" + name]);
	}
	return canHandle;
};

var isNotAbsoluteOrServerRelative = function(mid) {
	return !/^(\/)|([^:\/]+:[\/]{2})/.test(mid);	// nothing starting with / or http://	
};
//Returns true if the aggregator supports the specified module id.  Apps can provide an 
//implementation of this method in the loader config to exclude selected paths.
//Default is to support anything that doesn't begin with / or http://
var userSpecified = combo.isSupportedModule || function() { return true; };
combo.isSupportedModule = function(mid) {
	return isNotAbsoluteOrServerRelative(mid) && userSpecified(mid);
};

/*
 * If ary is null, then hash specifies the module id list hash on the server.
 * If ary is not null, then it specifies the module ids to register and hash is ignored
 */
combo.reg = function(ary, hash) {
	if (ary === null) {
		for (var s in moduleIdMap) {
			if (moduleIdMap.hasOwnProperty(s)) {
				throw new Error("Can't set hash");
			}
		}
		moduleIdMap["**idListHash**"] = hash;
		return;
	}
	registerModuleNameIds(ary, moduleIdMap);
};

combo.getIdMap = function() {
	// return a copy of the object
	// Note that this function is used only called unit tests and diagnostic tools, so 
	// the potentially poor performance of the converting the string to/form json is not
	// and issue.
	return JSON.parse(JSON.stringify(moduleIdMap));
};

/*
 * Decodes an aggregator request url.  Outputs to the console an object with properties 
 * identifying requested modules, defined features, etc.  Provided for diagnostic/debugging 
 * purposes.
 */
combo.decodeUrl = function(url) {
	require(['combo/dojo/requestDecoder'], function(decoder){
		console.log(decoder.decode(url));
	});
};

combo.isI18nResource = combo.isI18nResource || function(mid) {
	if (combo.plugins["combo/i18n"]) {
		// has combo/i18n plugin support
		return mid.prefix === "combo/i18n";
	} else {
		// no combo/i18n plugin support.  Figure it out from the module name
		return !mid.prefix && /.?\/nls\/.?/.test(mid.name); 
	}
};

combo.addBootLayerDeps = function(deps) {
	excludes = excludes.concat(deps);
};

combo.resetExcludeList = function() {
	// used for unit testing
	excludes = [];
}

/*
 * Returns true if the specified module is defined, false otherwise
 */
combo.isDefined = function(name) {
	try {
		require(name);
		return true;
	} catch (ignore) {
	}
	return false;
};

/*
 * Called by JAGGR responses before modules in the response will be defined.  <code>modules</code>
 * is the array of module ids that will be defined before combo.endDefs is called, in define order.
 * 
 * The goal here is to ensure that modules are defined in request order.  This is necessary in order 
 * to avoid the situation where additional, loader generated requests are generated to load unresolved
 * module dependencies that can result from out of order responses.  The order dependency comes from the
 * cumulative exclude list used to exclude previously requested modules.
 */
combo.beginDefs = function(modules) {
	if (!!currentLoadKey) {
		throw new Error("Invalid state in combo.beginDefs");
	}
	var key = modules.join(',');
	var index = pendingLoadKeys.indexOf(key);
	if (index == -1) {
		// Can happen when JAGGR returns something different than what was asked for,
		// for example, a version error module. 
		return;
	}
	if (index === 0 && (pendingLoadKeys.length === 1 || 
			pendingLoadKeys.every(function(pendingLoadKey) {
				return !pendingLoads[pendingLoadKey].defArgs;
			})
	)) {
		// There are no out-of-order pending requests, so just clean up the entries for this response
		// and return.
		delete pendingLoads[key];
		pendingLoadKeys.shift();
		return;
	}
	// Set the pending load key and defs array for this request.  define() function calls will be 
	// intercepted and the function arguments queued to the defs array for later play back.
	currentLoadKey = key;
	pendingLoads[key].defArgs = [];
};

/*
 * Called by JAGGR responses after the modules in the response have been defined (i.e. our define()
 * function intercept has been called).  If the current response is for the pending load at the 
 * head of the queue, then gather up all the adjacent completed responses and play back the define
 * function invocations for the combined modules to the original define function.
 * 
 * Note: this code depends on an implementation detail of the dojo loader which allows us to modify
 * the contents of the mid list that we provided to the combo.done() load callback after its been 
 * called but before the modules are defined.
 */
combo.endDefs = function() {
	if (currentLoadKey) {
		var key = currentLoadKey;
		currentLoadKey = null;		// clear the current key
		var index = pendingLoadKeys.indexOf(key);
		if (index === 0) {
			// Head request has completed.  Gather up adjacent requests that have completed into 
			// corresponding arrays.  Note that for the mids array, we are modifying the array 
			// that was originally passed to the loader via the combo.done() load callback.  We 
			// need to do this because the loader expects the modules to be defined in the order
			// that they are listed in the array.
			var mids = pendingLoads[key].mids;
			var defArgs = pendingLoads[key].defArgs;
			pendingLoadKeys.shift();
			delete pendingLoads[key];
			while (pendingLoadKeys.length && pendingLoads[pendingLoadKeys[0]].defArgs) {
				var pendingLoad = pendingLoads[pendingLoadKeys[0]];
				Array.prototype.push.apply(mids, pendingLoad.mids);	// add to existing array
				defArgs = defArgs.concat(pendingLoad.defArgs);
				// remove items from queues
				delete pendingLoads[pendingLoadKeys[0]];
				pendingLoadKeys.shift();
			}
			// Now invoke the original define function using the saved arguments for the 
			// combined modules.
			defArgs.forEach(function(args) {
				originalDefine.apply(this, args);
			});
		}
	}
};

setTimeout(function() {
	if (userConfig.deps) {
		require(userConfig.deps, function() {
			if (userConfig.callback) {
				userConfig.callback.apply(this, arguments);
			}
		});
	} else if (userConfig.callback) {
		userConfig.callback();
	}
}, 0);
})();
