
const PredictionModel = function () {

    const labelTypesToPredict = ['CurbRamp', 'NoCurbRamp', 'Obstacle', 'SurfaceProblem', 'NoSidewalk'];
    const CLUSTERING_THRESHOLDS = {
        'CurbRamp': 0.0035,
        'NoCurbRamp': 0.0035,
        'SurfaceProblem': 0.01,
        'Obstacle': 0.01,
        'NoSidewalk': 0.01,
    }
    const LABEL_TYPE_ONE_HOT = {
        'CurbRamp': [1, 0, 0, 0, 0],
        'NoCurbRamp': [0, 1, 0, 0, 0],
        'NoSidewalk': [0, 0, 1, 0, 0],
        'Obstacle': [0, 0, 0, 1, 0],
        'SurfaceProblem': [0, 0, 0, 0, 1]
    }
    const WAY_TYPE_ONE_HOT = {
        'living_street': [1, 0, 0, 0, 0, 0, 0],
        'primary': [0, 1, 0, 0, 0, 0, 0],
        'residential': [0, 0, 1, 0, 0, 0, 0],
        'secondary': [0, 0, 0, 1, 0, 0, 0],
        'tertiary': [0, 0, 0, 0, 1, 0, 0],
        'trunk': [0, 0, 0, 0, 0, 1, 0],
        'unclassified': [0, 0, 0, 0, 0, 0, 1]
    }

    // Vertical distance between the label and the popup.
    const popupVerticalOffset = 55;

    const $predictionModelPopupContainer = $('.prediction-model-popup-container');
    const $commonMistakesPopup = $('.common-mistakes-popup-container');
    const $commonMistakesCurrentLabelImage = $('.common-mistakes-current-label-image');

    const $gsvLayer = $('.gsv-layer');

    const $panorama = $('.window-streetview');
    const panoWidth = $panorama.width();
    const panoHeight = $panorama.height();

    let session = null;
    let inputParam = null;
    let outputParam = null;
    let clusters = null;


    // Important variable to track the current label.
    let currentLabel = null;

    const predictionModelExamplesDescriptor  = {
        'CurbRamp': {
            'subtitle': 'Tip: The #1 curb ramp mistake is labeling <b>driveways</b> as curb ramps.',
            'correct-examples': [
                {
                    'image': 'assets/images/tutorials/curbramp-correct-1.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
                {
                    'image': 'assets/images/tutorials/curbramp-correct-1.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
                {
                    'image': 'assets/images/tutorials/curbramp-correct-1.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
                {
                    'image': 'assets/images/tutorials/curbramp-correct-1.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
            ],
            'incorrect-examples': [
                {
                    'image': 'assets/images/tutorials/curbramp-incorrect-2.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
                {
                    'image': 'assets/images/tutorials/curbramp-incorrect-2.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
                {
                    'image': 'assets/images/tutorials/curbramp-incorrect-2.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
                {
                    'image': 'assets/images/tutorials/curbramp-incorrect-2.png',
                    'description': 'The curb ramp is clearly visible and is not obstructed by any objects.'
                },
            ],
        },
        'NoCurbRamp': {
            'subtitle': '48% of curb ramps are missed by users.',
            'correct-examples': [],
            'incorrect-examples': [],
        },
        'Obstacle': {
            'subtitle': '48% of curb ramps are missed by users.',
            'correct-examples': [],
            'incorrect-examples': [],
        },
        'SurfaceProblem': {
            'subtitle': '48% of curb ramps are missed by users.',
            'correct-examples': [],
            'incorrect-examples': [],
        },
        'NoSidewalk': {
            'subtitle': '48% of curb ramps are missed by users.',
            'correct-examples': [],
            'incorrect-examples': [],
        },
        'Crosswalk': {
            'subtitle': '48% of curb ramps are missed by users.',
            'correct-examples': [],
            'incorrect-examples': [],
        },
        'Signal': {
            'subtitle': '48% of curb ramps are missed by users.',
            'correct-examples': [],
            'incorrect-examples': [],
        }
    };


    async function predict(data) {
        // Use an async context to call onnxruntime functions.
        try {

            // Prepare inputs. A tensor need its corresponding TypedArray as data.
            // TODO distance_to_road after close_to_cluster
            // TODO distance_to_intersection after distance_to_road
            // TODO tag after distance_to_intersection
            // TODO way_type AFTER label_type
            var input = [data.severity, data.zoom, data.close_to_cluster, 0, 5, data.tag_count, data.has_description];
            input = input.concat(LABEL_TYPE_ONE_HOT[data.label_type]);
            input = input.concat(WAY_TYPE_ONE_HOT['residential']);

            const dataA = Float32Array.from(data = input);
            const tensorA = new ort.Tensor('float32', dataA, [1, input.length]);

            // Prepare feeds. use model input names as keys.
            const feeds = { };
            feeds[inputParam] = tensorA;

            // Feed inputs and run.
            const results = await session.run(feeds, [outputParam]);

            // Output from results.
            return results.output_label.data[0];
        } catch (e) {
            alert(`failed to inference ONNX model: ${e}.`);
        }
    }

    // We may not support prediction for all label types. Hence this check.
    function isPredictionSupported(labelType) {
        // Simple logic to see if support a label type--if it is defined in the descriptor.
        return predictionModelExamplesDescriptor[labelType] !== undefined;
    }

    // Check if the label is close to a cluster.
    function isCloseToCluster (lat, lng, labelType) {
        if (clusters[labelType].features.length > 0) {
            var latLng = turf.point([lng, lat]);
            var closest = turf.nearestPoint(latLng, clusters[labelType]);
            return closest.properties.distanceToPoint < CLUSTERING_THRESHOLDS[labelType];
        } else {
            return false;
        }
    }


    // Calls the predict function and depending on the result, shows the popup UI.
    function predictAndShowUI (data, label, svl) {
        disableInteractionsForPredictionModelPopup();

        // If prediction is not supported for the label type, return.
        // @Mikey, please check if this is correct.
        if (!isPredictionSupported(label.getProperties().labelType)) {
            return;
        }

        if (session === null || clusters === null) {
            alert('Please load a model first.');
            // Maybe we should log this.
        }

        const t1 = new Date().getTime();

        // Check if the label is close to a cluster.
        data.close_to_cluster = isCloseToCluster(data.lat, data.lng, data.label_type);

        const predictedScore = predict(data);

        predictedScore.then((score) => {
            console.log(score);

            console.log('Time elapsed: ' + (new Date().getTime() - t1).toString()); // Should this be logged on the server?

            currentLabel = label;
            if (score < 0.5) {
                enableInteractionsForPredictionModelPopup();
            } else {
                const labelProps = currentLabel.getProperties();

                $('.label-type', $predictionModelPopupContainer).text(i18next.t(`common:${util.camelToKebab(labelProps.labelType)}`));
                $('.prediction-model-popup-text', $predictionModelPopupContainer).html(predictionModelExamplesDescriptor[labelProps.labelType].subtitle); // this could contain HTML.

                $predictionModelPopupContainer.show();

                const popupHeight = $predictionModelPopupContainer.height();

                const left = labelProps.currCanvasXY.x - 24;
                const top = labelProps.currCanvasXY.y - popupHeight - popupVerticalOffset;
                $predictionModelPopupContainer.css({left: left, top: top});

            }
        });
    }

    function showExamples(labelType, correctOrIncorrect) {

        function renderExamples(examples) {

            $('.example-unit-container:not(.template)', $commonMistakesPopup).remove();

            for (let i = 0; i < examples.length; i++) {

                const $exampleUnit = $('.example-unit-container.template').clone().removeClass('template');

                const example = examples[i];
                $('img', $exampleUnit).attr('src', example.image);
                $('.example-unit-description', $exampleUnit).text(example.description);

                $exampleUnit.addClass(`unit-${i.toString()}`);

                $('.examples-panel-container', $commonMistakesPopup).append($exampleUnit);
            }
        }

        if (correctOrIncorrect === 'correct') {
            renderExamples(predictionModelExamplesDescriptor[labelType]['correct-examples']);

            $('.examples-panel-title-text').text('Correct Examples');
            $('.examples-panel-title-icon.common-mistakes-icon').hide();
            $('.examples-panel-title-icon.correct-examples-icon').show();

            $('.current-page', $commonMistakesPopup).text('2/2');
            $('.correct-examples-button', $commonMistakesPopup).addClass('disabled');
            $('.common-mistakes-button', $commonMistakesPopup).removeClass('disabled');

            $('.examples-panel-container').removeClass('common-mistakes').addClass('correct-examples');

        } else {
            renderExamples(predictionModelExamplesDescriptor[labelType]['incorrect-examples']);

            $('.examples-panel-title-text').text('Common Mistakes');
            $('.examples-panel-title-icon.correct-examples-icon').hide();
            $('.examples-panel-title-icon.common-mistakes-icon').show();

            $('.current-page', $commonMistakesPopup).text('1/2');
            $('.correct-examples-button', $commonMistakesPopup).removeClass('disabled');
            $('.common-mistakes-button', $commonMistakesPopup).addClass('disabled');

            $('.examples-panel-container').removeClass('correct-examples').addClass('common-mistakes');
        }

        $('.examples-panel-container').addClass(correctOrIncorrect);
    }

    function showCommonMistakesPopup(labelType) {

        // Shows and positions the icon on the current label screenshot.
        function showCurrentLabelScreenshot() {

            // Only positions the icon on the pano screenshot.
            // Should be called once the image is loaded.
            function positionCurrentLabelIcon() {
                const currentLabelProps = currentLabel.getProperties();
                const labelCanvasXY = currentLabelProps.currCanvasXY;
                const scaledX = (labelCanvasXY.x * $gsvLayer.width()) / panoWidth;
                const scaledY = (labelCanvasXY.y * $gsvLayer.height()) / panoHeight;

                $('.common-mistakes-current-label-icon', $commonMistakesCurrentLabelImage).css({
                    'left': scaledX,
                    'top': scaledY,
                });
            }

            // Show the current label screenshot from the GSV.
            const gsvLayerSrc = (function convertCanvasToImage(canvas) {
                return canvas.toDataURL('image/png', 1);
            })($('.widget-scene-canvas')[0]);

            $('img.gsv-layer', $commonMistakesCurrentLabelImage).attr('src', gsvLayerSrc).on('load', function () {
                positionCurrentLabelIcon();
            });

            const iconImagePath = util.misc.getIconImagePaths(labelType).iconImagePath;
            $('.common-mistakes-current-label-icon', $commonMistakesCurrentLabelImage)
                .attr('src', iconImagePath).show();

        }

        $('.common-mistakes-current-label-title .current-label-type', $commonMistakesPopup).text(i18next.t(`common:${util.camelToKebab(labelType)}`));

        // Shows the current label screenshot along with the label.
        showCurrentLabelScreenshot();

        // Show the common mistakes examples for the current label type.
        showExamples(labelType, 'incorrect');

        $commonMistakesPopup.show();
    }

    function disableInteractionsForPredictionModelPopup() {
        svl.map.disablePanning();
        svl.map.disableWalking();
        svl.ribbon.disableModeSwitch();
        svl.zoomControl.disableZoomIn();
        svl.zoomControl.disableZoomOut();
    }

    function enableInteractionsForPredictionModelPopup() {
        svl.map.enablePanning();
        svl.map.enableWalking();
        svl.ribbon.enableModeSwitch();
        svl.zoomControl.enableZoomIn();
        svl.zoomControl.enableZoomOut();
    }

    // Attaches all the UI event handlers. This should be called only once.
    // There should not be any other place where event handlers are attached.
    // Event handlers also take care of logging.
    function attachEventHandlers() {

        function hidePredictionModelPopup() {
            $predictionModelPopupContainer.hide();
        }

        function isCommonMistakesPopupOpenShown() {
            return $commonMistakesPopup.is(':visible');
        }

        $('.prediction-model-mistake-no-button', $predictionModelPopupContainer).on('click', function (e) {
            svl.tracker.push('PMMistakeNo_Click', { 'labelProps': JSON.stringify(currentLabel.getProperties()) }, null);
            hidePredictionModelPopup();
            enableInteractionsForPredictionModelPopup();
        });

        $('.prediction-model-mistake-yes-button', $predictionModelPopupContainer).on('click', function (e) {
            svl.tracker.push('PMMistakeYes_Click', { 'labelProps': JSON.stringify(currentLabel.getProperties()) }, null);
            svl.labelContainer.removeLabel(currentLabel);
            currentLabel = null;
            hidePredictionModelPopup();
            enableInteractionsForPredictionModelPopup();
        });

        $('.popup-close-button', $commonMistakesPopup).on('click', function (e) {

            e.preventDefault();
            e.stopPropagation();  // Stop propagation as we don't want to close the popup.

            $predictionModelPopupContainer.show();
            $commonMistakesPopup.hide();
        });

        $('.back-to-labeling-button', $commonMistakesPopup).on('click', function (e) {

            e.preventDefault();
            e.stopPropagation(); // Stop propagation as we don't want to close the popup.
            $predictionModelPopupContainer.show();
            $commonMistakesPopup.hide();
        });

        $('.prediction-model-view-examples-button').on('click', function (e) {
            svl.tracker.push('PMViewExamplesPopup_Click', { 'labelProps': JSON.stringify(currentLabel.getProperties()) }, null);
            const labelType = currentLabel.getProperties().labelType;
            showCommonMistakesPopup(labelType);
            hidePredictionModelPopup();
        });

        $('.common-mistakes-button').on('click', function (e) {
            svl.tracker.push('PMViewCommonMistakes_Click', { 'labelProps': JSON.stringify(currentLabel.getProperties()) }, null);
            const labelType = currentLabel.getProperties().labelType;
            showExamples(labelType, 'incorrect');
        });

        $('.correct-examples-button').on('click', function (e) {
            svl.tracker.push('PMViewCorrectExamples_Click', { 'labelProps': JSON.stringify(currentLabel.getProperties()) }, null);
            const labelType = currentLabel.getProperties().labelType;
            showExamples(labelType, 'correct');
        });
    }

    async function loadModel() {
        session = await ort.InferenceSession.create('assets/images/Seattle_Prediction_MLP.onnx');
        inputParam = session.inputNames[0];
        outputParam = session.outputNames[0];
    }

    async function loadClusters() {
        // Read cluster data from geojson file and split the clusters based on label type.
        $.getJSON('assets/images/labels-route-4.geojson', function (data) {
            clusters = {};
            for (let labType in CLUSTERING_THRESHOLDS) {
                clusters[labType] = { 'type': 'FeatureCollection', 'features': [] };
            }

            // Sort the clusters into separate arrays for each label type.
            for (let i = 0; i < data.features.length; i++) {
                let labType = data.features[i].properties.labelType;
                clusters[labType].features.push(data.features[i]);
            }
        });
    }

    loadModel();
    loadClusters();
    attachEventHandlers();

    return {
        labelTypesToPredict: labelTypesToPredict,
        predictAndShowUI: predictAndShowUI
    };
}();
