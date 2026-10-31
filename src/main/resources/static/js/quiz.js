/**
 * Small, dependency-free multiple-choice quiz engine.
 *
 * The markup (views, buttons, labels) lives in Thymeleaf fragments (templates/fragments/quiz.html);
 * this module only fills in content and switches states. Exercise content is passed in as plain data:
 *
 *   createQuiz(rootElement, {
 *       questions: [{
 *           context, options: [...], answer: <index>, explanation,
 *           notes: [...],                      // optional: one comment per option
 *           patterns: [{ phrase, meaning }]    // optional: useful language patterns
 *       }],
 *       summary: (score, total) => ({ title, text }),   // multi-question quizzes only
 *       labels: { correct, incorrect, revealed }         // optional status texts
 *   });
 *
 * Every element hook is optional, so the same engine drives both the multi-question quiz
 * (progress + result screen) and the single-question scenario (notes + patterns, no result screen).
 *
 * The root element emits bubbling `quiz:start` (first answer) and `quiz:complete` ({ score, total })
 * events; analytics.js turns them into analytics events.
 *
 * In `context`, `explanation`, `notes` and pattern meanings, text wrapped in *asterisks* is
 * rendered as an English fragment (styled and marked with lang="en").
 */

const LETTERS = ['A', 'B', 'C', 'D', 'E'];
const DEFAULT_LABELS = { correct: 'Верно', incorrect: 'Не совсем', revealed: 'Правильный ответ' };

const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const scrollBehavior = () => (prefersReducedMotion() ? 'auto' : 'smooth');

/** Renders "Text with *English* fragments" safely into an element. */
function renderRichText(element, text) {
    element.replaceChildren();
    text.split('*').forEach((part, i) => {
        if (!part) {
            return;
        }
        if (i % 2 === 1) {
            const span = document.createElement('span');
            span.className = 'quiz-en';
            span.lang = 'en';
            span.textContent = part;
            element.append(span);
        } else {
            element.append(part);
        }
    });
}

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) {
        node.className = className;
    }
    if (text !== undefined) {
        node.textContent = text;
    }
    return node;
}

function english(tag, className, text) {
    const node = el(tag, className, text);
    node.lang = 'en';
    return node;
}

/** Scrolls the element into view only if its top is hidden (e.g. behind the sticky header). */
function keepInView(element) {
    const headerOffset = 90;
    const top = element.getBoundingClientRect().top;
    if (top < headerOffset || top > window.innerHeight * 0.6) {
        element.scrollIntoView({ block: 'start', behavior: scrollBehavior() });
    }
}

export function createQuiz(root, { questions, summary, labels: customLabels = {} }) {
    const labels = { ...DEFAULT_LABELS, ...customLabels };
    const q = selector => root.querySelector(selector);

    const views = {
        question: q('[data-quiz-view="question"]'),
        result: q('[data-quiz-view="result"]')
    };
    const ui = {
        counter: q('[data-quiz-counter]'),
        progress: q('[data-quiz-progress]'),
        context: q('[data-quiz-context]'),
        options: q('[data-quiz-options]'),
        feedback: q('[data-quiz-feedback]'),
        verdict: q('[data-quiz-verdict]'),
        answer: q('[data-quiz-answer]'),
        explanation: q('[data-quiz-explanation]'),
        notes: q('[data-quiz-notes]'),
        patterns: q('[data-quiz-patterns]'),
        next: q('[data-quiz-next]'),
        nextLabel: q('[data-quiz-next-label]'),
        resultTitle: q('[data-quiz-result-title]'),
        score: q('[data-quiz-score]'),
        total: q('[data-quiz-total]'),
        summary: q('[data-quiz-summary]'),
        review: q('[data-quiz-review]')
    };

    const state = {
        index: 0,
        answers: questions.map(() => null)
    };

    const isCorrect = i => state.answers[i] === questions[i].answer;
    const score = () => state.answers.filter((_, i) => isCorrect(i)).length;

    // Lifecycle events (bubbling, so analytics.js can listen on document). The engine itself knows nothing about analytics.
    function emit(type, detail = {}) {
        root.dispatchEvent(new CustomEvent(type, { bubbles: true, detail: { exercise: root.dataset.quiz, ...detail } }));
    }

    function showView(name) {
        Object.entries(views).forEach(([key, view]) => {
            if (view) {
                view.hidden = key !== name;
            }
        });
        root.dataset.state = name;
    }

    function renderProgress() {
        if (!ui.progress) {
            return;
        }
        ui.progress.replaceChildren(...questions.map((_, i) => {
            const step = el('li', 'quiz__step');
            if (state.answers[i] !== null) {
                step.classList.add(isCorrect(i) ? 'is-correct' : 'is-incorrect');
            } else if (i === state.index) {
                step.classList.add('is-current');
            }
            return step;
        }));
    }

    function renderQuestion() {
        const question = questions[state.index];
        const last = state.index === questions.length - 1;

        if (ui.counter) {
            ui.counter.textContent = `Вопрос ${state.index + 1} из ${questions.length}`;
        }
        if (ui.nextLabel) {
            ui.nextLabel.textContent = last ? 'Посмотреть результат' : 'Следующий вопрос';
        }
        renderRichText(ui.context, question.context);
        ui.feedback.hidden = true;

        ui.options.replaceChildren(...question.options.map((text, i) => {
            const button = el('button', 'quiz-option');
            button.type = 'button';
            button.dataset.index = String(i);

            const letter = el('span', 'quiz-option__letter', LETTERS[i]);
            letter.setAttribute('aria-hidden', 'true');
            const status = el('span', 'quiz-option__status');

            button.append(letter, english('span', 'quiz-option__text', text), status);
            button.addEventListener('click', () => choose(i));

            const item = el('li');
            item.append(button);
            return item;
        }));

        renderProgress();
        restartAnimation(views.question);
    }

    function renderNotes(question, choice) {
        if (!ui.notes || !question.notes) {
            return;
        }
        ui.notes.replaceChildren(...question.options.map((text, i) => {
            const best = i === question.answer;
            const item = el('li', `quiz-note ${best ? 'is-best' : ''}`.trim());

            const head = el('p', 'quiz-note__head');
            const letter = el('span', 'quiz-note__letter', LETTERS[i]);
            letter.setAttribute('aria-hidden', 'true');
            head.append(letter, el('span', 'visually-hidden', `Вариант ${LETTERS[i]}`));
            if (best) {
                head.append(el('span', 'quiz-note__tag', 'Пример ответа'));
            }
            if (i === choice) {
                head.append(el('span', 'quiz-note__tag quiz-note__tag--yours', 'Ваш выбор'));
            }

            const comment = el('p', 'quiz-note__text');
            renderRichText(comment, question.notes[i]);

            item.append(head, english('p', 'quiz-note__phrase', text), comment);
            return item;
        }));
    }

    function renderPatterns(question) {
        if (!ui.patterns || !question.patterns) {
            return;
        }
        ui.patterns.replaceChildren(...question.patterns.map(({ phrase, meaning }) => {
            const item = el('li', 'quiz-pattern');
            const description = el('span', 'quiz-pattern__meaning');
            renderRichText(description, meaning);
            item.append(english('span', 'quiz-pattern__phrase', phrase), description);
            return item;
        }));
    }

    function choose(choice) {
        if (state.answers[state.index] !== null) {
            return; // answer is locked once feedback is shown
        }
        const question = questions[state.index];
        const firstAnswer = state.answers.every(answer => answer === null);
        state.answers[state.index] = choice;
        const correct = choice === question.answer;

        ui.options.querySelectorAll('.quiz-option').forEach(button => {
            const i = Number(button.dataset.index);
            const status = button.querySelector('.quiz-option__status');
            button.setAttribute('aria-disabled', 'true');

            if (i === question.answer) {
                button.classList.add('is-correct');
                status.textContent = i === choice ? 'Ваш ответ · верно' : labels.revealed;
            } else if (i === choice) {
                button.classList.add('is-incorrect');
                status.textContent = 'Ваш ответ';
            } else {
                button.classList.add('is-muted');
            }
        });

        const tone = correct ? 'is-correct' : 'is-incorrect';
        root.dataset.result = correct ? 'correct' : 'incorrect';
        ui.verdict.textContent = correct ? labels.correct : labels.incorrect;
        ui.verdict.className = `quiz__verdict ${tone}`;
        ui.feedback.className = `quiz__feedback ${tone}`;
        if (ui.answer) {
            ui.answer.textContent = question.options[question.answer];
        }
        renderRichText(ui.explanation, question.explanation);
        renderNotes(question, choice);
        renderPatterns(question);
        ui.feedback.hidden = false;

        renderProgress();
        ui.feedback.focus({ preventScroll: true });
        ui.feedback.scrollIntoView({ block: 'nearest', behavior: scrollBehavior() });

        if (firstAnswer) {
            emit('quiz:start');
        }
        if (!views.result && state.index === questions.length - 1) {
            emit('quiz:complete', { score: score(), total: questions.length }); // single-question scenario
        }
    }

    function next() {
        if (state.answers[state.index] === null) {
            return;
        }
        if (state.index < questions.length - 1) {
            state.index += 1;
            renderQuestion();
            ui.context.focus({ preventScroll: true });
            keepInView(root);
        } else if (views.result) {
            showResult();
        }
    }

    function showResult() {
        const total = questions.length;
        const points = score();
        const { title, text } = summary(points, total);

        ui.score.textContent = String(points);
        ui.total.textContent = String(total);
        ui.summary.replaceChildren(el('strong', null, title), ' ', text);
        root.dataset.score = String(points);
        emit('quiz:complete', { score: points, total });

        ui.review.replaceChildren(...questions.map((question, i) => {
            const correct = isCorrect(i);
            const item = el('li', `quiz-review ${correct ? 'is-correct' : 'is-incorrect'}`);

            const mark = el('span', 'quiz-review__mark', correct ? '✓' : '✕');
            mark.setAttribute('aria-hidden', 'true');
            const body = el('div', 'quiz-review__body');
            body.append(el('span', 'visually-hidden', correct ? 'Верно: ' : 'Ошибка: '));
            body.append(english('span', 'quiz-review__phrase', question.options[question.answer]));

            if (!correct) {
                const yours = el('span', 'quiz-review__yours', 'Ваш вариант: ');
                yours.append(english('span', null, question.options[state.answers[i]]));
                body.append(yours);
            }

            item.append(mark, body);
            return item;
        }));

        showView('result');
        restartAnimation(views.result);
        ui.resultTitle.focus({ preventScroll: true });
        keepInView(root);
    }

    function restart() {
        state.index = 0;
        state.answers = questions.map(() => null);
        delete root.dataset.result;
        delete root.dataset.score;
        showView('question');
        renderQuestion();
        ui.context.focus({ preventScroll: true });
        keepInView(root);
    }

    function restartAnimation(view) {
        view.classList.remove('is-entering');
        void view.offsetWidth; // reflow so the entry animation can replay
        view.classList.add('is-entering');
    }

    ui.next?.addEventListener('click', next);
    root.querySelectorAll('[data-quiz-restart]').forEach(button => button.addEventListener('click', restart));

    showView('question');
    renderQuestion();
    root.classList.add('is-ready');

    return { restart };
}
