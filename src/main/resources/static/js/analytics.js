/**
 * Provider-neutral analytics hook. No analytics service is connected and nothing leaves the browser
 * until an adapter is registered (see README → "Analytics").
 *
 * Events and their properties:
 *   path_view          { page, path: 'general-english' | 'it-english' }
 *   exercise_start     { page, exercise }                  – first answer in an exercise
 *   exercise_complete  { page, exercise, score, total }
 *   telegram_click     { page, cta }                       – a click on a Telegram link; it does NOT mean
 *                                                            a message was sent or a student signed up
 *
 * Privacy: events carry only the page path and the values above — never names, message texts,
 * Telegram drafts, IP addresses or other personal data.
 *
 * Connecting a provider later:
 *   import { setAdapter } from '/js/analytics.js';
 *   setAdapter(event => provider.track(event.name, event.props));
 * Events recorded before the adapter is set are queued and delivered once it is registered.
 */

const debug = document.querySelector('meta[name="revo-analytics-debug"]')?.content === 'true';
const queue = [];
let adapter = null;

export function track(name, props = {}) {
    const event = { name, props: { page: location.pathname, ...props } };
    if (debug) {
        console.info('[analytics]', event.name, event.props);
    }
    if (adapter) {
        deliver(event);
    } else if (queue.length < 50) {
        queue.push(event);
    }
}

export function setAdapter(fn) {
    adapter = fn;
    queue.splice(0).forEach(deliver);
}

function deliver(event) {
    try {
        adapter(event);
    } catch (error) {
        if (debug) {
            console.warn('[analytics] adapter failed', error);
        }
    }
}

// Learning-path page visits (marked with data-analytics-page on <body>).
const pathPage = document.body?.dataset.analyticsPage;
if (pathPage) {
    track('path_view', { path: pathPage });
}

// Exercise events are emitted by quiz.js as bubbling DOM events.
document.addEventListener('quiz:start', ({ detail }) => {
    track('exercise_start', { exercise: detail.exercise });
});
document.addEventListener('quiz:complete', ({ detail }) => {
    track('exercise_complete', { exercise: detail.exercise, score: detail.score, total: detail.total });
});

// Telegram CTA clicks; data-cta names the button's location (header, hero, contact, …).
document.addEventListener('click', event => {
    const link = event.target instanceof Element ? event.target.closest('a[href^="https://t.me/"]') : null;
    if (link) {
        track('telegram_click', { cta: link.dataset.cta ?? 'other' });
    }
});
