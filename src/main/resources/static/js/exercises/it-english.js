/**
 * Scenario for the IT English page: reporting a slow API endpoint to a teammate.
 *
 * Single question with a comment for every option (`notes`) and useful phrases (`patterns`).
 * The UI is shared (see ../quiz.js and templates/fragments/quiz.html :: scenario).
 * Wrap English fragments in *asterisks* to style them as English.
 */
import { createQuiz } from '../quiz.js';

const questions = [
    {
        context: 'После недавнего деплоя один из API-эндпоинтов стал отвечать заметно медленнее. Объясните проблему коллеге в рабочем чате.',
        options: [
            'Hello. The endpoint is very slow now. It is because of deploy, I think. Please fix it.',
            'Hi! Since yesterday’s deploy, the /orders endpoint has been noticeably slower — response time went from about 200 ms to over a second. I suspect the new database query. Could you take a look with me?',
            'Hey, there is a problem with performance of endpoint after the deployment which was made recently, and I think that maybe the reason can be in the query, but I’m not sure, so it would be good if somebody could check it when possible.'
        ],
        answer: 1,
        explanation: 'Вариант B за пару предложений отвечает на всё, что нужно коллеге: что сломалось (какой эндпоинт), '
            + 'когда (после вчерашнего деплоя), насколько (цифры), в чём может быть причина (гипотеза) и что нужно от коллеги (конкретная просьба). '
            + 'Тон дружелюбный, а *Could you take a look with me?* приглашает разобраться вместе, а не перекладывает задачу.',
        notes: [
            'Слишком общо: непонятно, какой эндпоинт и насколько он медленный. *Please fix it* звучит как приказ, а перед *deploy* '
                + 'не хватает артикля. Лучше: *The /orders endpoint has been slow since yesterday’s deploy. Could you help me look into it?*',
            'Конкретно, коротко и вежливо. Цифры заменяют «очень медленно», *I suspect* аккуратно предлагает гипотезу, '
                + 'а просьба в конце понятна сразу.',
            'Грамматически почти без ошибок (не хватает *the*: *the performance of the endpoint*), но главное теряется в длинном '
                + 'предложении с пассивом и тремя оговорками подряд. Лучше разбить: *The /orders endpoint got slower after the latest deploy. '
                + 'It might be the new query — I’m not sure yet. Could someone take a look?*'
        ],
        patterns: [
            { phrase: 'X has been slower since …', meaning: 'проблема началась в определённый момент и продолжается' },
            { phrase: 'Response time went from … to …', meaning: 'цифры вместо *very slow*' },
            { phrase: 'I suspect … / It might be …', meaning: 'гипотеза без категоричности' },
            { phrase: 'Could you take a look (with me)?', meaning: 'вежливая и конкретная просьба' },
            { phrase: 'I’m not sure yet.', meaning: 'честно обозначить неуверенность — одной короткой фразой' }
        ]
    }
];

document.querySelectorAll('[data-quiz="it-english"]').forEach(root => {
    createQuiz(root, {
        questions,
        labels: { correct: 'Точно в цель', incorrect: 'Можно яснее', revealed: 'Лучший вариант' }
    });
});
