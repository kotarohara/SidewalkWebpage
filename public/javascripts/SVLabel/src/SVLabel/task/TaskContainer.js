/**
 * TaskContainer module.
 *
 * Todo. This module needs to be cleaned up.
 * Todo. Split the responsibilities. Storing tasks should remain here, but other things like fetching data from the server (should go to TaskModel) and rendering segments on a map.
 * @param turf
 * @returns {{className: string}}
 * @constructor
 * @memberof svl
 */
function TaskContainer (streetViewService, svl, taskModel, tracker) {
    var self = { className: "TaskContainer" };
    var previousTasks = [];
    var currentTask = null;
    var paths;
    var previousPaths = [];

    self._taskStoreByRegionId = {};

    /**
     * I had to make this method to wrap the street view service.
     * @param task The current task
     */
    function initNextTask (task) {
        var nextTask = getNextTask(task);
        var geometry;
        var lat;
        var lng;

        var currentPosition = svl.map.getPosition();
        nextTask.setStreetEdgeDirection(currentPosition.lat, currentPosition.lng);

        geometry = nextTask.getGeometry();
        lat = geometry.coordinates[0][1];
        lng = geometry.coordinates[0][0];

        var STREETVIEW_MAX_DISTANCE = 25;
        var latLng = new google.maps.LatLng(lat, lng);

        if (streetViewService) {
            streetViewService.getPanoramaByLocation(latLng, STREETVIEW_MAX_DISTANCE, function (streetViewPanoramaData, status) {
                if (status === google.maps.StreetViewStatus.OK) {
                    setCurrentTask(nextTask);
                    svl.map.setPosition(streetViewPanoramaData.location.latLng.lat(), streetViewPanoramaData.location.latLng.lng());
                } else if (status === google.maps.StreetViewStatus.ZERO_RESULTS) {
                    // no street view available in this range.
                    initNextTask();
                } else {
                    throw "Error loading Street View imagey.";
                }
            });
        }
    }

    /**
     * End the current task.
     */
    function endTask (task) {
        if (tracker) tracker.push("TaskEnd");
        var neighborhood = svl.neighborhoodContainer.getCurrentNeighborhood();

        task.complete();
        // Go through the tasks and mark the completed task as isCompleted=true
        var neighborhoodTasks = self._taskStoreByRegionId[neighborhood.getProperty("regionId")];
        for (var i = 0, len = neighborhoodTasks.length;  i < len; i++) {
            if (task.getStreetEdgeId() == neighborhoodTasks[i].getStreetEdgeId()) {
                neighborhoodTasks[i].complete();
            }
        }

        // Update the total distance across neighborhoods that the user has audited
        updateAuditedDistance("miles");

        if (!('user' in svl) || (svl.user.getProperty('username') == "anonymous" && getCompletedTaskDistance(neighborhood.getProperty("regionId"), "kilometers") > 0.15)) {
            if (!svl.popUpMessage.haveAskedToSignIn()) svl.popUpMessage.promptSignIn();
        } else {
            // Submit the data.
            var data = svl.form.compileSubmissionData(task),
                staged = svl.storage.get("staged");

            if (staged.length > 0) {
                staged.push(data);
                svl.form.submit(staged, task);
                svl.storage.set("staged", []);  // Empty the staged data.
            } else {
                svl.form.submit(data, task);
            }
        }

        pushATask(task); // Push the data into previousTasks

        // Clear the current paths
        var _geojson = task.getGeoJSON(),
            gCoordinates = _geojson.features[0].geometry.coordinates.map(function (coord) { return new google.maps.LatLng(coord[1], coord[0]); });
        previousPaths.push(new google.maps.Polyline({ path: gCoordinates, geodesic: true, strokeColor: '#00ff00', strokeOpacity: 1.0, strokeWeight: 2 }));
        paths = null;

        return task;
    }


    /**
     * Fetch a task based on the street id.
     * @param regionId
     * @param streetEdgeId
     * @param callback
     * @param async
     */
    function fetchATask(regionId, streetEdgeId, callback, async) {
        if (typeof async == "undefined") async = true;
        $.ajax({
            url: "/task/street/" + streetEdgeId,
            type: 'get',
            success: function (json) {
                var lat1 = json.features[0].geometry.coordinates[0][1],
                    lng1 = json.features[0].geometry.coordinates[0][0],
                    newTask = svl.taskFactory.create(json, lat1, lng1);
                if (json.features[0].properties.completed) newTask.complete();
                storeTask(regionId, newTask);
                if (callback) callback();
            },
            error: function (result) {
                throw result;
            }
        });
    }

    /**
     * Request the server to populate tasks
     * @param regionId {number} Region id
     * @param callback A callback function
     * @param async {boolean}
     */
    function fetchTasksInARegion(regionId, callback, async) {
        if (typeof async == "undefined") async = true;

        if (typeof regionId == "number") {
            $.ajax({
                url: "/tasks?regionId=" + regionId,
                async: async,
                type: 'get',
                success: function (result) {
                    var task;
                    for (var i = 0; i < result.length; i++) {
                        task = svl.taskFactory.create(result[i]);
                        if ((result[i].features[0].properties.completed)) task.complete();
                        storeTask(regionId, task);
                    }

                    if (callback) callback();
                },
                error: function (result) {
                    console.error(result);
                }
            });
        } else {
            console.error("regionId should be an integer value");
        }
    }

    /**
     * Find tasks (i.e., street edges) in the region that are connected to the given task.
     * @param regionId {number} Region id
     * @param taskIn {object} Task
     * @param threshold {number} Distance threshold
     * @param unit {string} Distance unit
     * @returns {Array}
     */
    function findConnectedTask (regionId, taskIn, threshold, unit) {
        var tasks = getTasksInRegion(regionId),
            connectedTasks = [];

        if (!threshold) threshold = 0.01;  // 0.01 km.
        if (!unit) unit = "kilometers";
        tasks = tasks.filter(function (t) { return !t.isCompleted(); });

        if (taskIn) {
            tasks = tasks.filter(function (t) { return t.getStreetEdgeId() != taskIn.getStreetEdgeId(); });  // Filter out the current task

            for (var i = 0, len = tasks.length; i < len; i++) {
                if (taskIn.isConnectedTo(tasks[i], threshold, unit)) {
                    connectedTasks.push(tasks[i]);
                }
            }
            return connectedTasks;
        } else {
            return util.shuffle(tasks);
        }
    }

    /**
     * Get the total distance of completed segments
     * @params {unit} String can be degrees, radians, miles, or kilometers
     * @returns {number} distance in meters
     */
    function getCompletedTaskDistance (regionId, unit) {
        if (!unit) unit = "kilometers";

        var completedTasks = getCompletedTasks(regionId),
            geojson,
            feature,
            distance = 0;

        if (completedTasks) {
            for (var i = 0, len = completedTasks.length; i < len; i++) {
                geojson = completedTasks[i].getGeoJSON();
                feature = geojson.features[0];
                distance += turf.lineDistance(feature, unit);
            }
        }
        
        if (currentTask) {
            var currentLatLng = svl.map.getPosition();
            currentTask.updateTheFurthestPointReached(currentLatLng.lat, currentLatLng.lng);
            var currentTaskDistance = currentTask.getAuditedDistance(unit);
            distance += currentTaskDistance;
        }
        return distance;
    }

    /**
     * This method returns the completed tasks in the given region
     * @param regionId
     * @returns {Array}
     */
    function getCompletedTasks (regionId) {
        if (!(regionId in self._taskStoreByRegionId)) {
            console.error("getCompletedTasks needs regionId");
            return null;
        }
        if (!Array.isArray(self._taskStoreByRegionId[regionId])) {
            console.error("_taskStoreByRegionId[regionId] is not an array. Probably the data from this region is not loaded yet.");
            return null;
        }
        return self._taskStoreByRegionId[regionId].filter(function (task) {
            return task.isCompleted();
        });
    }

    /**
     * Get the current task
     * @returns {*}
     */
    function getCurrentTask () {
        return currentTask;
    }

    self.getIncompleteTaskDistance = function (regionId, unit) {
        var incompleteTasks = getIncompleteTasks(regionId);
        var taskDistances = incompleteTasks.map(function (task) { return task.lineDistance(unit); });
        return taskDistances.reduce(function (a, b) { return a + b; }, 0);
    };

    function getIncompleteTasks (regionId) {
        if (!regionId && regionId !== 0) {
            console.error("regionId is not specified")
        }
        if (!(regionId in self._taskStoreByRegionId)) {
            console.error("regionId is not in _taskStoreByRegionId. This is probably because you have not fetched the tasks in the region yet (e.g., by fetchTasksInARegion)");
            return null;
        }
        if (!Array.isArray(self._taskStoreByRegionId[regionId])) {
            console.error("_taskStoreByRegionId[regionId] is not an array. Probably the data from this region is not loaded yet.");
            return null;
        }
        return self._taskStoreByRegionId[regionId].filter(function (task) {
            return !task.isCompleted();
        });
    }

    function getTasksInRegion (regionId) {
        return regionId in self._taskStoreByRegionId ? self._taskStoreByRegionId[regionId] : null;
    }

    /**
     * Check if the current task is the first task in this session
     * @returns {boolean}
     */
    function isFirstTask () {
        return length() == 0;
    }

    /**
     * Get the length of the previous tasks
     * @returns {*|Number}
     */
    function length () {
        return previousTasks.length;
    }

    /**
     * Get the next task and set it as a current task.
     * @param task Current task
     * @returns {*} Next task
     */
    function getNextTask (task) {
        var newTask = null,
            neighborhood = svl.neighborhoodContainer.getCurrentNeighborhood(),
            candidateTasks = findConnectedTask(neighborhood.getProperty("regionId"), task, null, null);

        candidateTasks = candidateTasks.filter(function (t) { return !t.isCompleted(); });

        if (candidateTasks.length > 0) {
            // newTask = candidateTasks[0];
            newTask = _.shuffle(candidateTasks)[0];
        } else {
            candidateTasks = getIncompleteTasks(neighborhood.getProperty("regionId"));
            newTask = _.shuffle(candidateTasks)[0];
        }

        if (task) {
            var coordinate = task.getLastCoordinate();
            newTask.setStreetEdgeDirection(coordinate.lat, coordinate.lng);
        }

        return newTask;
    }

    /**
     * Push a task to previousTasks
     * @param task
     */
    function pushATask (task) {
        if (previousTasks.indexOf(task) < 0) {
            previousTasks.push(task);
        }
    }

    /**
     * Pop a task at the end of previousTasks
     * @returns {*}
     */
    function pop () {
        return previousTasks.pop();
    }

    /**
     * Set the current task
     * @param task
     */
    function setCurrentTask (task) {
        currentTask = task;
        if (tracker) tracker.push('TaskStart');

        if ('compass' in svl) {
            svl.compass.setTurnMessage();
            svl.compass.showMessage();
            svl.compass.update();
        }
    }

    /**
     * Store a task into taskStoreByRegionId
     * @param regionId {number} Region id
     * @param task {object} Task object
     */
    function storeTask(regionId, task) {
        if (!(regionId in self._taskStoreByRegionId)) self._taskStoreByRegionId[regionId] = [];
        var streetEdgeIds = self._taskStoreByRegionId[regionId].map(function (task) {
            return task.getStreetEdgeId();
        });
        if (streetEdgeIds.indexOf(task.getStreetEdgeId()) < 0) self._taskStoreByRegionId[regionId].push(task);  // Check for duplicates
    }

    /**
     *
     * @param regionId
     */
    function totalLineDistanceInARegion(regionId, unit) {
        if (!unit) unit = "kilometers";
        var tasks = getTasksInRegion(regionId);

        if (tasks) {
            var distanceArray = tasks.map(function (t) { return t.lineDistance(unit); });
            return distanceArray.sum();
        } else {
            return null;
        }
    }

    /**
     * This method is called from Map.handlerPositionUpdate() to update the color of audited and unaudited street
     * segments on Google Maps.
     * Todo. This should be done somewhere else.
     */
    function update () {
        for (var i = 0, len = previousTasks.length; i < len; i++) {
            previousTasks[i].render();
        }

        var currentLatLng = svl.map.getPosition();
        currentTask.updateTheFurthestPointReached(currentLatLng.lat, currentLatLng.lng);
        currentTask.render();
    }

    /**
     * Update the audited distance by combining the distance previously traveled and the distance the user traveled in
     * the current session.
     * Todo. Fix this. The function name should be clear that this updates the global distance rather than the distance traveled in the current neighborhood.
     * @returns {updateAuditedDistance}
     */
    function updateAuditedDistance (unit) {
        if (!unit) unit = "kilometers";
        var distance = 0,
            sessionDistance = 0,
            neighborhood = svl.neighborhoodContainer.getCurrentNeighborhood();

        if (neighborhood) {
            sessionDistance = getCompletedTaskDistance(neighborhood.getProperty("regionId"), unit);
        }

        distance += sessionDistance;
        svl.statusFieldNeighborhood.setAuditedDistance(distance.toFixed(1));
        return this;
    }

    self.initNextTask = initNextTask;
    self.endTask = endTask;
    self.fetchATask = fetchATask;
    self.fetchTasksInARegion = fetchTasksInARegion;
    self.findConnectedTask = findConnectedTask;
    self.getCompletedTasks = getCompletedTasks;
    self.getCompletedTaskDistance = getCompletedTaskDistance;
    self.getCurrentTask = getCurrentTask;
    self.getIncompleteTasks = getIncompleteTasks;
    self.getTasksInRegion = getTasksInRegion;
    self.isFirstTask = isFirstTask;
    self.length = length;
    self.nextTask = getNextTask;
    self.push = pushATask;

    self.setCurrentTask = setCurrentTask;
    self.storeTask = storeTask;
    self.totalLineDistanceInARegion = totalLineDistanceInARegion;
    self.update = update;
    self.updateAuditedDistance = updateAuditedDistance;

    return self;
}