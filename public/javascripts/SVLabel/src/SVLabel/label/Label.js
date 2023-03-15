/**
 * A Label module.
 * @param params
 * @returns {*}
 * @constructor
 * @memberof svl
 */
function Label(params) {
    var self = { className: 'Label' };

    var googleMarker;

    // Parameters determined from a series of linear regressions. Here links to the analysis and relevant Github issues:
    // https://github.com/ProjectSidewalk/label-latlng-estimation/blob/master/scripts/label-latlng-estimation.md#results
    // https://github.com/ProjectSidewalk/SidewalkWebpage/issues/2374
    // https://github.com/ProjectSidewalk/SidewalkWebpage/issues/2362
    var LATLNG_ESTIMATION_PARAMS = {
        1: {
            headingIntercept: -51.2401711,
            headingCanvasXSlope: 0.1443374,
            distanceIntercept: 18.6051843,
            distanceSvImageYSlope: 0.0138947,
            distanceCanvasYSlope: 0.0011023
        },
        2: {
            headingIntercept: -27.5267447,
            headingCanvasXSlope: 0.0784357,
            distanceIntercept: 20.8794248,
            distanceSvImageYSlope: 0.0184087,
            distanceCanvasYSlope: 0.0022135
        },
        3: {
            headingIntercept: -13.5675945,
            headingCanvasXSlope: 0.0396061,
            distanceIntercept: 25.2472682,
            distanceSvImageYSlope: 0.0264216,
            distanceCanvasYSlope: 0.0011071
        }
    };
    var HOVER_INFO_HEIGHT = 20;

    var properties = {
        labelId: 'DefaultValue',
        auditTaskId: undefined,
        missionId: undefined,
        labelType: undefined,
        fillStyle: undefined,
        labelDescription: undefined,
        iconImagePath: undefined,
        originalCanvasCoordinate: undefined,
        currCanvasCoordinate: undefined,
        svImageCoordinate: undefined,
        originalPov: undefined,
        povOfLabelIfCentered: undefined,
        labelLat: undefined,
        labelLng: undefined,
        latLngComputationMethod: undefined,
        panoId: undefined,
        panoramaLat: undefined,
        panoramaLng: undefined,
        photographerHeading: undefined,
        photographerPitch: undefined,
        svImageWidth: undefined,
        svImageHeight: undefined,
        tagIds: [],
        severity: null,
        tutorial: null,
        temporaryLabelId: null,
        temporaryLabel: false,
        description: null
    };

    var status = {
        deleted : false,
        hoverInfoVisibility : 'visible',
        visibility : 'visible'
    };

    var hoverInfoProperties = util.misc.getSeverityDescription();

    function _init(param) {
        for (var attrName in param) {
            if (param.hasOwnProperty(attrName) && properties.hasOwnProperty(attrName)) {
                properties[attrName] = param[attrName];
            }
        }

        properties.iconImagePath = util.misc.getIconImagePaths(properties.labelType).iconImagePath;
        properties.fillStyle = util.misc.getLabelColors()[properties.labelType].fillStyle;

        // Calculate sv_image_x/y if the label is new.
        if (properties.svImageCoordinate === undefined) {
            var panoData = svl.panoramaContainer.getPanorama(properties.panoId).data();

            properties.svImageWidth = panoData.tiles.worldSize.width;
            properties.svImageHeight = panoData.tiles.worldSize.height;
            properties.svImageCoordinate = util.panomarker.calculateImageCoordinateFromPointPov(
                properties.povOfLabelIfCentered, properties.svImageWidth, properties.svImageHeight
            );
        }

        // Create the marker on the minimap.
        if (typeof google !== "undefined" && google && google.maps) {
            googleMarker = createMinimapMarker(properties.labelType);
            googleMarker.setMap(svl.map.getMap());
        }
    }

    /**
     * This method creates a Google Maps marker.
     * https://developers.google.com/maps/documentation/javascript/markers
     * https://developers.google.com/maps/documentation/javascript/examples/marker-remove
     * @returns {google.maps.Marker}
     */
    function createMinimapMarker (labelType) {
        if (typeof google !== "undefined") {
            var latlng = toLatLng();
            var googleLatLng = new google.maps.LatLng(latlng.lat, latlng.lng);

            var imagePaths = util.misc.getIconImagePaths(),
                url = imagePaths[labelType].minimapIconImagePath;

            return new google.maps.Marker({
                position: googleLatLng,
                map: svl.map.getMap(),
                title: "Hi!",
                icon: url,
                size: new google.maps.Size(20, 20)
            });
        }
    }

    /**
     * This function returns the coordinate the label.
     * @returns {*}
     */
    function getCoordinate() {
        return properties.currCanvasCoordinate;
    }

    /**
     * This function returns labelId property
     * @returns {string}
     */
    function getLabelId () {
        return properties.labelId;
    }

    /**
     * This function returns labelType property
     * @returns {*}
     */
    function getLabelType () { return properties.labelType; }

    /**
     * This function returns panoId property
     * @returns {*}
     */
    function getPanoId () { return properties.panoId; }

    /**
     * Return deep copy of properties obj, so one can only modify props from setProperties() (not yet implemented).
     * JavaScript Deepcopy
     * http://stackoverflow.com/questions/122102/what-is-the-most-efficient-way-to-clone-a-javascript-object
     */
    function getProperties () { return $.extend(true, {}, properties); }

    /**
     * Get a property
     * @param propName
     */
    function getProperty(propName) { return (propName in properties) ? properties[propName] : false; }

    /**
     * Get a status
     * @param key
     * @returns {*}
     */
    function getStatus (key) {
        return status[key];
    }

    function getVisibility () { return status.visibility; }

    /**
     * Check if the label is deleted
     * @returns {boolean}
     */
    function isDeleted () { return status.deleted; }


    /**
     * Check if a label is under a cursor
     * @param x
     * @param y
     * @returns {boolean}
     */
    function isOn (x, y) {
        if (status.deleted || status.visibility === 'hidden') {  return false; }
        var margin = svl.LABEL_ICON_RADIUS / 2 + 2;
        if (x < properties.currCanvasCoordinate.x + margin &&
            x > properties.currCanvasCoordinate.x - margin &&
            y < properties.currCanvasCoordinate.y + margin &&
            y > properties.currCanvasCoordinate.y - margin) {
            return this;
        } else {
            return false;
        }
    }

    /**
     * This method returns the visibility of this label.
     * @returns {boolean}
     */
    function isVisible () {
        return status.visibility === 'visible';
    }

    /**
     * Remove the label (it does not actually remove, but hides the label and set its status to 'deleted').
     */
    function remove () {
        setStatus('deleted', true);
        setStatus('visibility', 'hidden');
    }

    /**
     * This method renders this label on a canvas.
     * @param ctx
     * @param pov
     * @returns {self}
     */
    function render(ctx, pov) {
        if (!status.deleted && status.visibility === 'visible') {
            if (status.hoverInfoVisibility === 'visible') {
                // Render hover info and delete button.
                renderHoverInfo(ctx);
                showDeleteButton();
            }

            // Update the coordinates of the label on the canvas.
            if (svl.map.getPovChangeStatus()) {
                properties.currCanvasCoordinate = util.panomarker.getCanvasCoordinate(
                    properties.povOfLabelIfCentered, pov, util.EXPLORE_CANVAS_WIDTH, util.EXPLORE_CANVAS_HEIGHT, svl.LABEL_ICON_RADIUS
                );
            }

            // Draw the label icon.
            var imageObj, imageHeight, imageWidth, imageX, imageY;
            imageObj = new Image();
            imageHeight = imageWidth = 2 * svl.LABEL_ICON_RADIUS - 3;
            imageX =  properties.currCanvasCoordinate.x - svl.LABEL_ICON_RADIUS + 2;
            imageY = properties.currCanvasCoordinate.y - svl.LABEL_ICON_RADIUS + 2;

            imageObj.src = getProperty('iconImagePath');

            try {
                ctx.drawImage(imageObj, imageX, imageY, imageHeight, imageWidth);
            } catch (e) {
                console.debug(e);
            }

            // Draws label outline.
            ctx.beginPath();
            ctx.fillStyle = getProperty('fillStyle');
            ctx.lineWidth = 0.7;
            ctx.beginPath();
            ctx.arc(properties.currCanvasCoordinate.x, properties.currCanvasCoordinate.y, 15.3, 0, 2 * Math.PI);
            ctx.strokeStyle = 'black';
            ctx.stroke();
            ctx.beginPath();
            ctx.arc(properties.currCanvasCoordinate.x, properties.currCanvasCoordinate.y, 16.2, 0, 2 * Math.PI);
            ctx.strokeStyle = 'white';
            ctx.stroke();

            // Only render severity warning if there's a severity option.
            if (!['Occlusion', 'Signal'].includes(properties.labelType) && properties.severity === null) {
                showSeverityAlert(ctx);
            }
        }

        // Show a label on the google maps pane.
        if (!isDeleted()) {
            if (googleMarker && !googleMarker.map) {
                googleMarker.setMap(svl.map.getMap());
            }
        } else {
            if (googleMarker && googleMarker.map) {
                googleMarker.setMap(null);
            }
        }
        return this;
    }

    /**
     * This function renders hover info on a canvas to show an overview of the label info.
     *
     * @param ctx
     * @returns {boolean}
     */
    function renderHoverInfo(ctx) {
        if ('contextMenu' in svl && svl.contextMenu.isOpen()) {
            return false;
        }

        // labelCoordinate represents the upper left corner of the hover info.
        var labelCoordinate = getCoordinate(),
            cornerRadius = 3,
            hasSeverity = (properties.labelType !== 'Occlusion' && properties.labelType !== 'Signal'),
            i, height,
            width = 0,
            labelRows = 1,
            severityImage = new Image(),
            severitySVGElement,
            severityMessage = i18next.t('center-ui.context-menu.severity'),
            msg = i18next.t(util.camelToKebab(properties.labelType) + '-description'),
            messages = msg.split('\n'),
            padding = { left: 12, right: 5, bottom: 0, top: 18 };

        if (hasSeverity) {
            labelRows = 2;
            if (properties.severity !== null) {
                severitySVGElement = $(`.severity-icon.template.severity-${properties.severity}`).clone().removeClass('template').find('svg');
                severityImage.src = 'data:image/svg+xml; charset=utf8, ' + encodeURIComponent($(severitySVGElement).prop('outerHTML'));
                severityMessage = hoverInfoProperties[properties.severity].message;
            }
        }

        // Set rendering properties and draw the hover info.
        ctx.font = '13px Open Sans';
        height = HOVER_INFO_HEIGHT * labelRows;

        for (i = 0; i < messages.length; i += 1) {
            // Width of the hover info is determined by the width of the longest row.
            var firstRow = ctx.measureText(messages[i]).width;
            var secondRow = -1;

            // Do additional adjustments on the width to make room for smiley icon.
            if (hasSeverity) {
                secondRow = ctx.measureText(severityMessage).width;
                if (severitySVGElement != undefined) {
                    if (firstRow - secondRow > 0 && firstRow - secondRow < 15) {
                        width += 15 - firstRow + secondRow;
                    } else if (firstRow - secondRow < 0) {
                        width += 20;
                    }
                }
            }

            width += Math.max(firstRow, secondRow) + 5;
        }

        ctx.lineCap = 'square';
        ctx.lineWidth = 2;
        ctx.fillStyle = util.misc.getLabelColors(getProperty('labelType'));
        ctx.strokeStyle = 'rgba(255,255,255,1)';


        // Hover info background.
        ctx.beginPath();
        ctx.moveTo(labelCoordinate.x + cornerRadius, labelCoordinate.y);
        ctx.lineTo(labelCoordinate.x + width + padding.left + padding.right - cornerRadius, labelCoordinate.y);
        ctx.arc(labelCoordinate.x + width + padding.left + padding.right, labelCoordinate.y + cornerRadius, cornerRadius, 3 * Math.PI / 2, 0, false); // Corner
        ctx.lineTo(labelCoordinate.x + width + padding.left + padding.right + cornerRadius, labelCoordinate.y + height + padding.bottom);
        ctx.arc(labelCoordinate.x + width + padding.left + padding.right, labelCoordinate.y + height + cornerRadius, cornerRadius, 0, Math.PI / 2, false); // Corner
        ctx.lineTo(labelCoordinate.x + cornerRadius, labelCoordinate.y + height + 2 * cornerRadius);
        ctx.arc(labelCoordinate.x + cornerRadius, labelCoordinate.y + height + cornerRadius, cornerRadius, Math.PI / 2, Math.PI, false);
        ctx.lineTo(labelCoordinate.x, labelCoordinate.y + cornerRadius);
        ctx.fill();
        ctx.stroke();
        ctx.closePath();

        // Hover info text and image.
        ctx.fillStyle = '#ffffff';
        ctx.fillText(messages[0], labelCoordinate.x + padding.left, labelCoordinate.y + padding.top);
        if (hasSeverity) {
            ctx.fillText(severityMessage, labelCoordinate.x + padding.left, labelCoordinate.y + HOVER_INFO_HEIGHT + padding.top);
            if (properties.severity !== null) {
                ctx.drawImage(severityImage, labelCoordinate.x + padding.left +
                    ctx.measureText(severityMessage).width + 5, labelCoordinate.y + 25, 16, 16);
            }
        }
    }

    /**
     * Sets a property
     * @param key
     * @param value
     * @returns {setProperty}
     */
    function setProperty (key, value) {
        properties[key] = value;
        return this;
    }

    /**
     * Set status
     * @param key
     * @param value
     */
    function setStatus (key, value) {
        if (key in status) {
            if (key === 'visibility' && (value === 'visible' || value === 'hidden')) {
                setVisibility(value);
            } else if (key === 'hoverInfoVisibility' && (value === 'visible' || value === 'hidden')) {
                setHoverInfoVisibility(value);
            } else if (key === 'deleted' && typeof value === 'boolean') {
                status[key] = value;
            } else if (key === 'severity') {
                status[key] = value;
            }
        }
    }

    /**
     * Set the visibility of the hover info.
     * @param visibility {string} visible or hidden
     * @returns {setHoverInfoVisibility}
     */
    function setHoverInfoVisibility (visibility) {
        if (visibility === 'visible' || visibility === 'hidden') {
            status['hoverInfoVisibility'] = visibility;
        }
        return this;
    }

    /**
     * Set the visibility of the label
     * @param visibility
     * @returns {setVisibility}
     */
    function setVisibility (visibility) {
        status.visibility = visibility;
        return this;
    }

    /**
     * Set visibility of labels
     * @param visibility
     * @param panoramaId
     * @returns {setVisibilityBasedOnLocation}
     */
    function setVisibilityBasedOnLocation (visibility, panoramaId) {
        if (!status.deleted) {
            if (panoramaId === properties.panoId) {
                setVisibility(visibility);
            } else {
                visibility = visibility === 'visible' ? 'hidden' : 'visible';
                setVisibility(visibility);
            }
        }
        return this;
    }

    function showDeleteButton() {
        if (status.hoverInfoVisibility !== 'hidden') {
            var coord = getCoordinate();
            svl.ui.canvas.deleteIconHolder.css({
                visibility: 'visible',
                left : coord.x + 5,
                top : coord.y - 20
            });
        }
    }

    /**
     * Renders a question mark if a label has an unmarked severity
     * @param ctx   Rendering tool for severity (2D context)
     */
    function showSeverityAlert(ctx) {
        var labelCoordinate = getCoordinate();
        var x = labelCoordinate.x;
        var y = labelCoordinate.y;

        // Draws circle.
        ctx.beginPath();
        ctx.fillStyle = 'rgb(160, 45, 50, 0.9)';
        ctx.ellipse(x - 15, y - 10.5, 8, 8, 0, 0, 2 * Math.PI);
        ctx.fill();
        ctx.closePath();

        // Draws text
        ctx.beginPath();
        ctx.font = "12px Open Sans";
        ctx.fillStyle = 'rgb(255, 255, 255)';
        ctx.fillText('?', x - 17.5, y - 6);
        ctx.closePath();
    }

    /**
     * Get the label latlng position
     * @returns {labelLatLng}
     */
    function toLatLng() {
        if (!properties.labelLat) {
            // Estimate the latlng point from the camera position and the heading angle when the point cloud data is not available.
            var panoLat = getProperty("panoramaLat");
            var panoLng = getProperty("panoramaLng");
            var panoHeading = getProperty("originalPov").heading;
            var zoom = getProperty("originalPov").zoom;
            var canvasX = getProperty('originalCanvasCoordinate').x;
            var canvasY = getProperty('originalCanvasCoordinate').y;
            var svImageY = getProperty('svImageCoordinate').y;

            // Estimate heading diff and distance from pano using output from a regression analysis.
            // https://github.com/ProjectSidewalk/label-latlng-estimation/blob/master/scripts/label-latlng-estimation.md#results
            var estHeadingDiff =
                LATLNG_ESTIMATION_PARAMS[zoom].headingIntercept +
                LATLNG_ESTIMATION_PARAMS[zoom].headingCanvasXSlope * canvasX;
            var estDistanceFromPanoKm = Math.max(0,
                LATLNG_ESTIMATION_PARAMS[zoom].distanceIntercept +
                LATLNG_ESTIMATION_PARAMS[zoom].distanceSvImageYSlope * svImageY +
                LATLNG_ESTIMATION_PARAMS[zoom].distanceCanvasYSlope * canvasY
            ) / 1000.0;
            var estHeading = panoHeading + estHeadingDiff;
            var startPoint = turf.point([panoLng, panoLat]);

            // Use the pano location, distance from pano estimate, and heading estimate, calculate label location.
            var destination = turf.destination(startPoint, estDistanceFromPanoKm, estHeading, { units: 'kilometers' });
            var latlng = {
                lat: destination.geometry.coordinates[1],
                lng: destination.geometry.coordinates[0],
                latLngComputationMethod: 'approximation2'
            };
            setProperty('labelLat', latlng.lat);
            setProperty('labelLng', latlng.lng);
            setProperty('latLngComputationMethod', latlng.latLngComputationMethod);
            return latlng;
        } else {
            // Return the cached value.
            return {
                lat: getProperty('labelLat'),
                lng: getProperty('labelLng'),
                latLngComputationMethod: getProperty('latLngComputationMethod')
            };
        }

    }

    self.getCoordinate = getCoordinate;
    self.getLabelId = getLabelId;
    self.getLabelType = getLabelType;
    self.getPanoId = getPanoId;
    self.getProperties = getProperties;
    self.getProperty = getProperty;
    self.getstatus = getStatus;
    self.getVisibility = getVisibility;
    self.isDeleted = isDeleted;
    self.isOn = isOn;
    self.isVisible = isVisible;
    self.render = render;
    self.remove = remove;
    self.setProperty = setProperty;
    self.setStatus = setStatus;
    self.setHoverInfoVisibility = setHoverInfoVisibility;
    self.setVisibility = setVisibility;
    self.setVisibilityBasedOnLocation = setVisibilityBasedOnLocation;
    self.toLatLng = toLatLng;

    _init(params);
    return self;
}
