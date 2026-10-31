/**
 * Exercise for the General English page: everyday phrases that are often translated word for word.
 *
 * To edit the exercise, change the data below — the quiz UI is shared (see ../quiz.js).
 * `answer` is the zero-based index of the natural option. Wrap English fragments in
 * *asterisks* inside `explanation` to style them as English.
 */
import { createQuiz } from '../quiz.js';

const questions = [
    {
        context: 'Вы хотите сказать, что согласны с собеседником.',
        options: [
            'I am agree with you.',
            'I agree with you.',
            'I am agreeing with you.'
        ],
        answer: 1,
        explanation: '*Agree* — это глагол, а не прилагательное, поэтому *am* перед ним не нужен: *I agree*, *I don’t agree*. '
            + 'Форма *I’m agreeing* встречается редко: глаголы мнения и состояния обычно не ставят в Continuous.'
    },
    {
        context: 'Вы хотите предложить сделать перерыв.',
        options: [
            'Let’s make a break.',
            'Let’s do a break.',
            'Let’s take a break.'
        ],
        answer: 2,
        explanation: 'По-английски перерыв «берут»: *take a break*. Такие устойчивые сочетания (*take a break*, *make a decision*, '
            + '*do homework*) проще запоминать целиком, чем выбирать глагол по смыслу. *Make a break* встречается в других значениях, '
            + 'например *make a break for it* — «рвануть, сбежать».'
    },
    {
        context: 'Вы хотите сказать, что вам скучно.',
        options: [
            'I am boring.',
            'I am bored.',
            'I feel boring.'
        ],
        answer: 1,
        explanation: 'Окончание *-ed* описывает чувство человека, а *-ing* — то, что это чувство вызывает: *The film was boring, so I was bored.* '
            + '*I am boring* значит «я скучный человек». Та же пара: *interested / interesting*, *tired / tiring*.'
    },
    {
        context: 'Вы хотите спросить, как сказать что-то по-английски.',
        options: [
            'How to say this in English?',
            'How do you say this in English?',
            'How it is in English?'
        ],
        answer: 1,
        explanation: 'В вопросе с *how* нужен вспомогательный глагол: *How do you say…?*, *How do you spell…?* '
            + 'Короткое *How to say…?* встречается в заголовках и поисковых запросах, но в разговоре звучит обрывисто. '
            + 'Ещё естественный вариант: *What’s the English for…?*'
    }
];

function summary(score, total) {
    if (score === total) {
        return {
            title: 'Без ошибок.',
            text: 'Эти фразы вы уже строите естественно. Дальше интереснее всего работать с беглостью и живым разговором.'
        };
    }
    if (score >= total - 2) {
        return {
            title: 'Неплохо.',
            text: 'Ошибки здесь появляются из-за дословного перевода с русского — разбор ниже показывает, как устроена каждая фраза.'
        };
    }
    return {
        title: 'Типичные ловушки.',
        text: 'Эти фразы почти всегда переводят слово в слово, поэтому ошибаются в них очень многие. Посмотрите разбор ниже.'
    };
}

document.querySelectorAll('[data-quiz="general-english"]').forEach(root => {
    createQuiz(root, { questions, summary });
});
