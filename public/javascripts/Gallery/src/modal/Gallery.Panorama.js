/**
 * A holder class that inserts a GSV Pano into the supplied DOM element
 * 
 * @param {HTMLElement} svHolder The DOM element that the GSV Pano will be placed in
 * @returns {GalleryPanorama} The gallery panorama that was generated
 */
 function GalleryPanorama(svHolder) {
    let self = {
        className: "GalleryPanorama",
        label: undefined,
        labelMarkers: undefined,
        panoId: undefined,
        panorama: undefined,
    };

    const icons = {
        CurbRamp : '/assets/images/icons/AdminTool_CurbRamp.png',
        NoCurbRamp : '/assets/images/icons/AdminTool_NoCurbRamp.png',
        Obstacle : '/assets/images/icons/AdminTool_Obstacle.png',
        SurfaceProblem : '/assets/images/icons/AdminTool_SurfaceProblem.png',
        Other : '/assets/images/icons/AdminTool_Other.png',
        Occlusion : '/assets/images/icons/AdminTool_Other.png',
        NoSidewalk : '/assets/images/icons/AdminTool_NoSidewalk.png'
    };

    // Determined experimentally; varies w/ GSV Panorama size
    const zoomLevel = {
        1: 1,
        2: 1.95,
        3: 2.95
    };

    /**
     * This function initializes the Panorama
     */
    function _init () {
        console.log('loading')
        self.svHolder = $(svHolder);
        self.svHolder.addClass("admin-panorama");

        // svHolder's children are absolutely aligned, svHolder's position has to be either absolute or relative
        if(self.svHolder.css('position') != "absolute" && self.svHolder.css('position') != "relative")
            self.svHolder.css('position', 'relative');

        console.log(self.svHolder.height())
        // GSV will be added to panoCanvas
        self.panoCanvas = $("<div id='pano'>").css({
            position: 'relative',
            top: '0px',
            width: '100%',
            height: '60vh'
        })[0];

        self.panoNotAvailable = $("<div id='pano-not-avail'>Oops, our fault but there is no longer imagery available " +
            "for this label.</div>").css({
            'font-size': '200%',
            'padding-bottom': '15px'
        })[0];

        self.panoNotAvailableDetails =
            $("<div id='pano-not-avail-2'>We use the Google Maps API to show the sidewalk images and sometimes Google" +
                " removes these images so we can no longer access them. Sorry about that.</div>").css({
            'font-size': '85%',
            'padding-bottom': '15px'
        })[0];

        self.svHolder.append($(self.panoCanvas));
        self.svHolder.append($(self.panoNotAvailable));
        self.svHolder.append($(self.panoNotAvailableDetails));

        self.panorama = typeof google != "undefined" ? new google.maps.StreetViewPanorama(self.panoCanvas, { mode: 'html4' }) : null;
        self.panorama.addListener('pano_changed', function() {
            // Show the correct set of labels for the given pano.
            const currentPano = self.panorama.getPano();
            const marker = self.labelMarkers
            if (marker !== undefined) {
                if (marker.panoId === currentPano) {
                    marker.marker.setVisible(true);
                } else {
                    marker.marker.setVisible(false);
                }
            }

        });

        if (self.panorama) {
            self.panorama.set('addressControl', false);
            self.panorama.set('clickToGo', false);
            self.panorama.set('disableDefaultUI', true);
            self.panorama.set('linksControl', false);
            self.panorama.set('navigationControl', false);
            self.panorama.set('panControl', false);
            self.panorama.set('zoomControl', false);
            self.panorama.set('keyboardShortcuts', false);
            self.panorama.set('motionTracking', false);
            self.panorama.set('motionTrackingControl', false);
            self.panorama.set('showRoadLabels', false);

            // Disable moving by clicking if on /labelmap, enable if on admin page.
            self.panorama.set('clickToGo', false)
        }

        return this;
    }

    function setPov(heading, pitch, zoom) {
        self.panorama.set('pov', {heading: heading, pitch: pitch});
        self.panorama.set('zoom', zoomLevel[zoom]);
    }

    /**
     * Sets the panorama ID and POV from label metadata
     * @param panoId
     * @param heading
     * @param pitch
     * @param zoom
     */
    function setPano(panoId, heading, pitch, zoom) {
        if (typeof google != "undefined") {
            self.panorama.registerPanoProvider(function(pano) {
                if (pano === 'tutorial' || pano === 'afterWalkTutorial') {
                    return getCustomPanorama(pano);
                }

                return null;
            });
            
            self.svHolder.css('visibility', 'hidden');
            self.panoId = panoId;

            self.panorama.setPano(panoId);
            self.panorama.set('pov', {heading: heading, pitch: pitch});
            self.panorama.set('zoom', zoomLevel[zoom]);

            // Based off code from Onboarding.
            // We write another callback function because of a bug in the Google Maps API that
            // causes the screen to go black.
            // This callback gives time for the pano to load for 500ms. Afterwards, we trigger a
            // resize and reset the POV/Zoom.
            function callback (n) {
                google.maps.event.trigger(self.panorama, 'resize');
                self.panorama.set('pov', {heading: heading, pitch: pitch});
                self.panorama.set('zoom', zoomLevel[zoom]);
                self.svHolder.css('visibility', 'visible');

                // Show pano if it exists, an error message if there is no GSV imagery, and another error message if we
                // wait a full 2 seconds without getting a response from Google.
                if (self.panorama.getStatus() === "OK") {
                    $(self.panoCanvas).css('display', 'block');
                    $(self.panoNotAvailable).css('display', 'none');
                    $(self.panoNotAvailableDetails).css('display', 'none');
                    if (self.label) renderLabel(self.label);
                } else if (self.panorama.getStatus() === "ZERO_RESULTS") {
                    $(self.svHolder).css('height', '');
                    $(self.panoNotAvailable).text('Oops, our fault but there is no longer imagery available for this label.');
                    $(self.panoCanvas).css('display', 'none');
                    $(self.panoNotAvailable).css('display', 'block');
                    $(self.panoNotAvailableDetails).css('display', 'block');
                } else if (n < 1) {
                    $(self.svHolder).css('height', '');
                    $(self.panoNotAvailable).text('We had trouble connecting to Google Street View, please try again later!');
                    $(self.panoCanvas).css('display', 'none');
                    $(self.panoNotAvailable).css('display', 'block');
                    $(self.panoNotAvailableDetails).css('display', 'none');
                } else {
                    setTimeout(callback, 200, n - 1);
                }
            }
            setTimeout(callback, 200, 10);
        }
        return this;
    }

    function setLabel (label) {
        self.label = label;
    }

    /**
     * Renders a Panomarker (label) onto Google Streetview Panorama.
     * @param label: instance of AdminPanoramaLabel
     * @returns {renderLabel}
     */
    function renderLabel (label) {
        const url = icons[label['label_type']];
        const pos = getPosition(label['canvasX'], label['canvasY'], label['originalCanvasWidth'],
            label['originalCanvasHeight'], label['zoom'], label['heading'], label['pitch']);

        self.labelMarkers = {
            panoId: self.panorama.getPano(),
            marker: new PanoMarker({
                container: self.panoCanvas,
                pano: self.panorama,
                position: {heading: pos.heading, pitch: pos.pitch},
                icon: url,
                size: new google.maps.Size(20, 20),
                anchor: new google.maps.Point(10, 10)
            })
        };
        return this;
    }

    /**
     * Calculates heading and pitch for a Google Maps marker using (x, y) coordinates
     * From PanoMarker spec
     * @param canvas_x          X coordinate (pixel) for label
     * @param canvas_y          Y coordinate (pixel) for label
     * @param canvas_width      Original canvas width
     * @param canvas_height     Original canvas height
     * @param zoom              Original zoom level of label
     * @param heading           Original heading of label
     * @param pitch             Original pitch of label
     * @returns {{heading: number, pitch: number}}
     */
    function getPosition(canvas_x, canvas_y, canvas_width, canvas_height, zoom, heading, pitch) {
        function sgn(x) {
            return x >= 0 ? 1 : -1;
        }

        const PI = Math.PI;
        let cos = Math.cos;
        let sin = Math.sin;
        let tan = Math.tan;
        let sqrt = Math.sqrt;
        let atan2 = Math.atan2;
        let asin = Math.asin;
        const fov = get3dFov(zoom) * PI / 180.0;
        const width = canvas_width;
        const height = canvas_height;
        const h0 = heading * PI / 180.0;
        const p0 = pitch * PI / 180.0;
        const f = 0.5 * width / tan(0.5 * fov);
        const x0 = f * cos(p0) * sin(h0);
        const y0 = f * cos(p0) * cos(h0);
        const z0 = f * sin(p0);
        const du = (canvas_x) - width / 2;
        const dv = height / 2 - (canvas_y - 5);
        const ux = sgn(cos(p0)) * cos(h0);
        const uy = -sgn(cos(p0)) * sin(h0);
        const uz = 0;
        const vx = -sin(p0) * sin(h0);
        const vy = -sin(p0) * cos(h0);
        const vz = cos(p0);
        const x = x0 + du * ux + dv * vx;
        const y = y0 + du * uy + dv * vy;
        const z = z0 + du * uz + dv * vz;
        const R = sqrt(x * x + y * y + z * z);
        const h = atan2(x, y);
        const p = asin(z / R);
        return {
            heading: h * 180.0 / PI,
            pitch: p * 180.0 / PI
        };
    }

    /**
     * This calculates the heading and position for placing this Label onto the panorama from the same POV as when the
     * user placed the label.
     * @returns {{heading: number, pitch: number}}
     */
    function getOriginalPosition () {
        return getPosition(self.label['canvasX'], self.label['canvasY'], self.label['originalCanvasWidth'],
            self.label['originalCanvasHeight'], self.label['zoom'], self.label['heading'], self.label['pitch']);
    }

    /**
     * From panomarker spec
     * @param zoom
     * @returns {number}
     */
    function get3dFov (zoom) {
        return zoom <= 2 ?
            126.5 - zoom * 36.75 :  // linear descent
            195.93 / Math.pow(1.92, zoom); // parameters determined experimentally
    }

    //init
    _init();

    self.setPov = setPov;
    self.setPano = setPano;
    self.setLabel = setLabel;
    self.renderLabel = renderLabel;
    self.getOriginalPosition = getOriginalPosition;
    return self;
}
