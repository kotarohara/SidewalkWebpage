/**
 * An indicator that displays the speed limit of the current position's nearest road.
 *
 * @param {StreetViewPanorama} panorama Panorama object.
 * @param {function} coords Function that returns current longitude and latitude coordinates.
 * @param {function} isOnboarding Function that returns a boolean on whether the current mission is the tutorial task.
 * @returns {SpeedLimit} SpeedLimit object with updateSpeedLimit function, container, speedLimit object with
 * number and sub (units, e.g. 'mph'), speedLimitVisible boolean.
 */
function SpeedLimit(panorama, coords, isOnboarding) {
    const ROAD_HIGHWAY_TYPES = [
        'motorway',
        'trunk',
        'primary',
        'secondary',
        'tertiary',
        'unclassified',
        'residential',
        'motorway_link',
        'trunk_link',
        'primary_link',
        'secondary_link',
        'tertiary_link',
        'living_street',
        'road'
    ]

    let self = this;

    function _init() {
        self.container = document.getElementById('speed-limit-sign');
        self.speedLimit = {
            number: '',
            sub: ''
        };
        self.speedLimitVisible = false
        updateSpeedLimit();

        // Listen for position changes.
        panorama.addListener('position_changed', positionChange);
    }

    /**
     * Render/update the speed limit using the current info in speedLimit.
     */
    function updateSpeedLimit() {
        self.container.querySelector('#speed-limit').innerText = self.speedLimit.number;
        self.container.querySelector('#speed-limit-sub').innerText = self.speedLimit.sub;
        self.container.style.display = self.speedLimitVisible ? 'flex' : 'none';
    }

    /**
     * Finds the closest road given overpass API's response of nearby roads and the current position.
     *
     * @param {Object} data The overpass API's response of nearby roads.
     * @param {Number} lat The latitude of the current position.
     * @param {Number} lon The longitude of the current position.
     */
    function findClosestRoad(data, lat, lon) {
        // Filter to only be roads, and not footpaths/walkways.
        const roads = data.elements.filter(el =>
            el.type === 'way' && el.tags && el.tags.highway && ROAD_HIGHWAY_TYPES.includes(el.tags.highway)
        );

        const point = turf.point([lat, lon])
        let closestRoad = null;
        let minDistance = Infinity;

        // Go through all the roads and find the closest one.
        for (const road of roads) {
            const lineString = turf.lineString(road.geometry.map(p => [p.lon, p.lat]));
            const distance = turf.pointToLineDistance(point, lineString);

            if (distance < minDistance) {
                minDistance = distance;
                closestRoad = road;
            }
        }

        return closestRoad;
    }

    /**
     * Function to be called on a position change/movement in the google street view.
     */
    async function positionChange() {
        // If user is in the onboarding/tutorial mission, we can skip getting the speed limit and hide the sign.
        if (isOnboarding()) {
            self.speedLimitVisible = false
            updateSpeedLimit()
            return
        }

        // Get the current position.
        const { lat, lng } = coords()
        // Test coords here if someone finds them useful.
        // const lat = 47.6271486
        // const lng = -122.3423263

        // Get nearby roads and their respective information from the overpass API.
        const overpassQuery = `
        [out:json];
        (
        way['highway'](around:10.0, ${lat}, ${lng});
        );
        out geom;
        is_in(${lat}, ${lng})->.a;
        rel(pivot.a)['ISO3166-1'];
        convert country
            ::id = id(),
            code = t['ISO3166-1'];
        out tags;
        `
        const overpassResp = await fetch(
            `https://overpass-api.de/api/interpreter?data=${encodeURIComponent(overpassQuery)}`
        )
        const overpassRespJson = await overpassResp.json()

        // Fallback units should be kilometers per hour by default.
        let fallbackUnits = 'km/h'

        // Get the country code of the current location to set the speed limit indicator design and fallback units.
        const countryElements = overpassRespJson.elements.filter((el) => el.type === 'country')
        if (countryElements.length > 0) {
            const countryCode = countryElements[0].tags.code

            // Set proper design.
            if (countryCode === 'US' || countryCode === 'CA') {
                self.container.setAttribute('data-design-style', 'us-canada')
            } else {
                self.container.setAttribute('data-design-style', 'non-us-canada')
            }

            // Set mph for fallback units if US or UK.
            if (countryCode === 'US' || countryCode === 'UK') {
                fallbackUnits = 'mph'
            }
        }

        // Extract speed limit info from closest road.
        const closestRoad = findClosestRoad(overpassRespJson, lat, lng);
        if (closestRoad !== null && closestRoad.tags['maxspeed']) {
            const splitMaxspeed = closestRoad.tags['maxspeed'].split(' ')
            const number = splitMaxspeed.shift()
            let sub = splitMaxspeed.join(' ')
            if (sub.trim().length === 0) {
                sub = fallbackUnits;
            }
            self.speedLimit = {
                number,
                sub
            };
            self.speedLimitVisible = true
        } else {
            self.speedLimitVisible = false
        }
        updateSpeedLimit()
    }

    _init();

    self.updateSpeedLimit = updateSpeedLimit;

    return self;
}