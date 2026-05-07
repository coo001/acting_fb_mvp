(function () {
    'use strict';

    const PRIMARY = 'rgb(0, 113, 227)';
    const PRIMARY_BG = 'rgba(0, 113, 227, 0.15)';
    const SECONDARY = 'rgb(255, 149, 0)';
    const SECONDARY_BG = 'rgba(255, 149, 0, 0.15)';
    const MAX_BYTES = 500 * 1024 * 1024;

    setupUploadForm();
    setupCompareSelect();
    setupJobPoller();
    renderChartsIfPresent();

    function setupUploadForm() {
        const form = document.getElementById('uploadForm');
        if (!form) return;
        form.addEventListener('submit', function (e) {
            const fileInput = document.getElementById('file');
            const labelInput = document.getElementById('label');
            const submitBtn = document.getElementById('submitBtn');
            const spinner = document.getElementById('spinner');

            const f = fileInput.files[0];
            if (f) {
                const name = f.name.toLowerCase();
                if (!name.endsWith('.mp4') && !name.endsWith('.mov')) {
                    e.preventDefault();
                    alert('mp4 또는 mov 파일만 가능');
                    return;
                }
                if (f.size > MAX_BYTES) {
                    e.preventDefault();
                    alert('500MB 초과는 못 보냄');
                    return;
                }
            }
            submitBtn.disabled = true;
            fileInput.disabled = true;
            labelInput.disabled = true;
            spinner.hidden = false;
        });
    }

    function setupCompareSelect() {
        const compareSelect = document.getElementById('compareSelect');
        const dataEl = document.getElementById('resultData');
        if (!compareSelect || !dataEl) return;
        const currentId = dataEl.dataset.currentId;
        compareSelect.addEventListener('change', function () {
            const v = compareSelect.value;
            const url = v
                ? '/result/' + encodeURIComponent(currentId) + '?compareWith=' + encodeURIComponent(v)
                : '/result/' + encodeURIComponent(currentId);
            window.location.href = url;
        });
    }

    function setupJobPoller() {
        const meta = document.getElementById('jobMeta');
        if (!meta) return;
        const jobId = meta.dataset.jobId;
        const phaseEl = document.getElementById('jobPhase');
        const errorEl = document.getElementById('jobError');
        const spinnerEl = document.getElementById('jobStatusSpinner');

        let timer = null;
        async function poll() {
            try {
                const res = await fetch('/api/jobs/' + encodeURIComponent(jobId));
                if (!res.ok) return;
                const job = await res.json();
                if (phaseEl && job.phase) phaseEl.textContent = job.phase;
                if (job.status === 'DONE' && job.resultId) {
                    if (timer) clearInterval(timer);
                    window.location.href = '/result/' + encodeURIComponent(job.resultId);
                } else if (job.status === 'FAILED') {
                    if (timer) clearInterval(timer);
                    if (spinnerEl) spinnerEl.style.display = 'none';
                    if (errorEl) {
                        errorEl.textContent = '분석 실패: ' + (job.errorMessage || '알 수 없는 에러');
                        errorEl.hidden = false;
                    }
                }
            } catch (e) {
                console.warn('폴링 실패', e);
            }
        }
        poll();
        timer = setInterval(poll, 2000);
    }

    function renderChartsIfPresent() {
        const dataEl = document.getElementById('resultData');
        if (!dataEl) return;
        if (typeof Chart === 'undefined') {
            console.error('Chart.js 로드 실패');
            return;
        }

        const current = JSON.parse(dataEl.dataset.current);
        const compare = dataEl.dataset.compare ? JSON.parse(dataEl.dataset.compare) : null;

        renderEmotion(current, compare);
        renderSpeech(current, compare);
        renderSilences(current, compare);
    }

    function renderEmotion(current, compare) {
        const el = document.getElementById('emotionChart');
        if (!el) return;
        const datasets = [{
            label: current.label,
            data: (current.emotionIntensity || []).map(p => ({ x: p.t, y: p.value })),
            borderColor: PRIMARY,
            backgroundColor: PRIMARY_BG,
            tension: 0.2,
            pointRadius: 2,
        }];
        if (compare) {
            datasets.push({
                label: compare.label,
                data: (compare.emotionIntensity || []).map(p => ({ x: p.t, y: p.value })),
                borderColor: SECONDARY,
                backgroundColor: SECONDARY_BG,
                tension: 0.2,
                pointRadius: 2,
            });
        }
        new Chart(el, {
            type: 'line',
            data: { datasets: datasets },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { type: 'linear', title: { display: true, text: '초' } },
                    y: { min: 0, max: 10, title: { display: true, text: '감정 강도' } }
                }
            }
        });
    }

    function renderSpeech(current, compare) {
        const el = document.getElementById('speechChart');
        if (!el) return;
        const datasets = [{
            label: current.label,
            data: (current.speechRate || []).map(p => ({ x: p.t, y: p.wpm })),
            borderColor: PRIMARY,
            backgroundColor: PRIMARY_BG,
            tension: 0.2,
            pointRadius: 2,
        }];
        if (compare) {
            datasets.push({
                label: compare.label,
                data: (compare.speechRate || []).map(p => ({ x: p.t, y: p.wpm })),
                borderColor: SECONDARY,
                backgroundColor: SECONDARY_BG,
                tension: 0.2,
                pointRadius: 2,
            });
        }
        new Chart(el, {
            type: 'line',
            data: { datasets: datasets },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { type: 'linear', title: { display: true, text: '초' } },
                    y: { title: { display: true, text: 'WPM' }, beginAtZero: true }
                }
            }
        });
    }

    function renderSilences(current, compare) {
        if (compare) {
            renderSilenceBar(document.getElementById('silenceCurrent'), current, PRIMARY);
            renderSilenceBar(document.getElementById('silenceCompare'), compare, SECONDARY);
        } else {
            renderSilenceBar(document.getElementById('silenceChart'), current, PRIMARY);
        }
    }

    function renderSilenceBar(canvas, result, color) {
        if (!canvas) return;
        const silences = result.silences || [];
        const labels = silences.map(s => s.start.toFixed(1) + 's');
        const data = silences.map(s => s.duration);
        new Chart(canvas, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: result.label,
                    data: data,
                    backgroundColor: color,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { title: { display: true, text: '시작 시각' } },
                    y: { title: { display: true, text: 'duration (초)' }, beginAtZero: true }
                }
            }
        });
    }
})();
