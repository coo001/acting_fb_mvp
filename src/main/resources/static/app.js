(function () {
    'use strict';

    const MAX_BYTES = 500 * 1024 * 1024;

    const CATEGORIES = [
        { key: 'gaze', label: '시선', color: '#5e9eff' },
        { key: 'head', label: '머리', color: '#ff9500' },
        { key: 'gesture', label: '제스처', color: '#af52de' },
        { key: 'posture', label: '자세', color: '#34c759' },
        { key: 'breath', label: '호흡', color: '#00b8d4' },
        { key: 'voice', label: '발성', color: '#ff3b30' },
        { key: 'emphasis', label: '강세', color: '#ffcc00' },
        { key: 'microexpression', label: '표정', color: '#ff2d92' },
        { key: 'tic', label: '버릇', color: '#8e8e93' }
    ];
    const COLOR_MAP = Object.fromEntries(CATEGORIES.map(c => [c.key, c.color]));
    const LABEL_MAP = Object.fromEntries(CATEGORIES.map(c => [c.key, c.label]));

    setupUploadForm();
    setupJobPoller();
    renderObservationResult();

    function setupUploadForm() {
        const form = document.getElementById('uploadForm');
        if (!form) return;
        form.addEventListener('submit', function (e) {
            const fileInput = document.getElementById('file');
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
            spinner.hidden = false;
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

    function renderObservationResult() {
        const dataEl = document.getElementById('resultData');
        if (!dataEl || !dataEl.dataset.current) return;
        const data = JSON.parse(dataEl.dataset.current);
        const video = document.getElementById('player');
        const observations = (data.observations || [])
            .slice()
            .sort((a, b) => a.startSec - b.startSec);
        const transcript = data.transcript || [];
        const duration = data.durationSec || 1;

        const obsCount = document.getElementById('obsCount');
        if (obsCount) obsCount.textContent = observations.length;

        renderTimeline(observations, duration, video);
        renderFilterAndList(observations, video);
        renderTranscript(transcript, video);
    }

    function renderTimeline(observations, duration, video) {
        const timeline = document.getElementById('timeline');
        if (!timeline) return;
        timeline.innerHTML = '';
        CATEGORIES.forEach(cat => {
            const row = document.createElement('div');
            row.className = 'timeline-row';

            const label = document.createElement('div');
            label.className = 'timeline-label';
            label.textContent = cat.label;
            label.style.borderLeftColor = cat.color;

            const track = document.createElement('div');
            track.className = 'timeline-track';

            observations
                .filter(o => o.category === cat.key)
                .forEach(o => {
                    const marker = document.createElement('div');
                    marker.className = 'timeline-marker';
                    const left = (o.startSec / duration) * 100;
                    const width = Math.max(0.4, ((o.endSec - o.startSec) / duration) * 100);
                    marker.style.left = left + '%';
                    marker.style.width = width + '%';
                    marker.style.background = cat.color;
                    marker.title = o.description + ' (' + o.startSec.toFixed(1) + 's)';
                    marker.addEventListener('click', () => seek(video, o.startSec));
                    track.appendChild(marker);
                });

            row.appendChild(label);
            row.appendChild(track);
            timeline.appendChild(row);
        });
    }

    function renderFilterAndList(observations, video) {
        const filterBar = document.getElementById('filterBar');
        const listEl = document.getElementById('observationList');
        if (!filterBar || !listEl) return;
        filterBar.innerHTML = '';

        let currentFilter = 'all';

        function makeFilterButton(key, labelText, color) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'filter-btn' + (key === currentFilter ? ' active' : '');
            btn.textContent = labelText;
            btn.dataset.key = key;
            btn.style.setProperty('--accent', color);
            btn.addEventListener('click', () => {
                currentFilter = key;
                filterBar.querySelectorAll('.filter-btn').forEach(b =>
                    b.classList.toggle('active', b.dataset.key === key));
                renderList();
            });
            return btn;
        }

        filterBar.appendChild(makeFilterButton('all', '전체', '#1d1d1f'));
        CATEGORIES.forEach(cat =>
            filterBar.appendChild(makeFilterButton(cat.key, cat.label, cat.color)));

        function renderList() {
            listEl.innerHTML = '';
            const items = observations.filter(o =>
                currentFilter === 'all' || o.category === currentFilter);
            if (items.length === 0) {
                const empty = document.createElement('li');
                empty.className = 'empty';
                empty.textContent = '해당 카테고리 관찰 없음';
                listEl.appendChild(empty);
                return;
            }
            items.forEach(o => {
                const li = document.createElement('li');
                li.className = 'observation-item';

                const time = document.createElement('button');
                time.type = 'button';
                time.className = 'time-jump';
                time.textContent = formatTime(o.startSec);
                time.addEventListener('click', () => seek(video, o.startSec));

                const badge = document.createElement('span');
                badge.className = 'category-badge';
                badge.textContent = LABEL_MAP[o.category] || o.category;
                badge.style.background = COLOR_MAP[o.category] || '#999';

                const desc = document.createElement('span');
                desc.className = 'observation-desc';
                desc.textContent = o.description;

                li.appendChild(time);
                li.appendChild(badge);
                li.appendChild(desc);
                listEl.appendChild(li);
            });
        }
        renderList();
    }

    function renderTranscript(transcript, video) {
        const trEl = document.getElementById('transcriptList');
        if (!trEl) return;
        trEl.innerHTML = '';
        if (transcript.length === 0) {
            const empty = document.createElement('li');
            empty.className = 'empty';
            empty.textContent = '트랜스크립트 없음';
            trEl.appendChild(empty);
            return;
        }
        transcript.forEach(t => {
            const li = document.createElement('li');
            li.className = 'transcript-item';

            const time = document.createElement('button');
            time.type = 'button';
            time.className = 'time-jump';
            time.textContent = formatTime(t.start);
            time.addEventListener('click', () => seek(video, t.start));

            const text = document.createElement('span');
            text.className = 'transcript-text';
            text.textContent = t.text;

            li.appendChild(time);
            li.appendChild(text);
            trEl.appendChild(li);
        });
    }

    function seek(video, sec) {
        if (!video) return;
        video.currentTime = Math.max(0, sec);
        video.play().catch(() => {});
    }

    function formatTime(sec) {
        const m = Math.floor(sec / 60);
        const s = Math.floor(sec % 60);
        return m + ':' + String(s).padStart(2, '0');
    }
})();
