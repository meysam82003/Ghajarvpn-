(function () {
    'use strict';

    var $ = function (sel, root) { return (root || document).querySelector(sel); };
    var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

    function initInstallFieldWizard() {
        var steps = $$('.field-step');
        if (!steps.length) return;

        var current = 1;
        var total = steps.length;

        function show() {
            current = Math.max(1, Math.min(total, current));
            steps.forEach(function (step, idx) {
                step.classList.toggle('is-active', idx === current - 1);
            });
            var prevBtn = $('#install-prev-btn');
            var nextBtn = $('#install-next-btn');
            var submitBtn = $('#install-submit');
            var progressText = $('#install-progress-text');
            var progressFill = $('#field-progress-fill');
            if (prevBtn) prevBtn.hidden = current === 1;
            if (nextBtn) nextBtn.hidden = current === total;
            if (submitBtn) submitBtn.hidden = current !== total;
            if (progressText) progressText.textContent = current;
            if (progressFill) progressFill.style.width = Math.round(current * 100 / total) + '%';
        }

        function validateCurrent() {
            var step = steps[current - 1];
            if (!step) return true;
            var inputs = $$('input[required], select[required], textarea[required]', step);
            for (var i = 0; i < inputs.length; i++) {
                var input = inputs[i];
                if (input.offsetParent === null && input.closest('details') && !input.closest('details').open) {
                    continue;
                }
                if (input.value.trim() === '') {
                    input.reportValidity();
                    return false;
                }
            }
            return true;
        }

        var nextBtn = $('#install-next-btn');
        var prevBtn = $('#install-prev-btn');
        if (nextBtn) {
            nextBtn.addEventListener('click', function () {
                if (!validateCurrent()) return;
                if (current < total) { current++; show(); }
            });
        }
        if (prevBtn) {
            prevBtn.addEventListener('click', function () {
                if (current > 1) { current--; show(); }
            });
        }

        show();
    }

    document.addEventListener('DOMContentLoaded', initInstallFieldWizard);
})();
