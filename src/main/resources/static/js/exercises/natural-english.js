/**
 * Exercise: «Звучит ли это естественно?»
 *
 * To edit the exercise, change the data below — the quiz UI is shared (see ../quiz.js).
 * `answer` is the zero-based index of the natural option. Wrap English fragments in
 * *asterisks* inside `explanation` to style them as English.
 */
import { createQuiz } from '../quiz.js';

const questions = [
    {
        context: 'Вы хотите сказать, что вам очень нравится фильм.',
        options: [
            'I very like this film.',
            'I really like this film.',
            'I like very this film.'
        ],
        answer: 1,
        explanation: '*Very* усиливает прилагательные и наречия (*very good*, *very fast*), но перед глаголом не ставится. '
            + 'Чтобы усилить *like*, говорят *really like* — или *like … very much*, причём *very much* идёт после дополнения: '
            + '*I like this film very much*.'
    },
    {
        context: 'Вас спрашивают, не голодны ли вы.',
        options: [
            'Yes, I have hunger.',
            'Yes, I am hungry.',
            'Yes, I feel myself hungry.'
        ],
        answer: 1,
        explanation: 'Голод, усталость, холод в английском обычно описывают через *be* + прилагательное: '
            + '*I’m hungry*, *I’m tired*, *I’m cold*. *I have hunger* — калька с «у меня голод»; существительное *hunger* '
            + 'уместно в других контекстах, например *world hunger*. А «чувствую себя голодным» — это *I feel hungry*, без *myself*.'
    },
    {
        context: 'Вы хотите сказать, что начали учить английский три года назад и учите до сих пор.',
        options: [
            'I learn English since three years.',
            'I am learning English for three years ago.',
            'I have been learning English for three years.'
        ],
        answer: 2,
        explanation: 'Действие началось в прошлом и продолжается сейчас, поэтому нужен Present Perfect Continuous: '
            + '*have been learning* + *for* (сколько длится) или *since* (с какого момента: *since 2022*). '
            + 'Русское настоящее время («учу уже три года») сюда дословно не переносится. '
            + '*Ago* сочетается с прошедшим временем: *I started three years ago*.'
    },
    {
        context: 'Вы рассказываете другу, что фильм вам понравился.',
        options: [
            'I very much enjoyed the film.',
            'I very enjoyed the film.',
            'I enjoyed very much the film.'
        ],
        answer: 0,
        explanation: 'Одно *very* глагол не усиливает, а *very much* — может: перед глаголом (*I very much enjoyed the film*) '
            + 'или после дополнения (*I enjoyed the film very much*). Между глаголом и дополнением наречие обычно не ставят, '
            + 'поэтому *enjoyed very much the film* звучит неестественно. В разговоре чаще всего скажут просто *I really enjoyed the film*.'
    },
    {
        context: 'Вы хотите сказать, что вам нужно сходить в магазин.',
        options: [
            'I must to go to the shop.',
            'I need go to the shop.',
            'I need to go to the shop.'
        ],
        answer: 2,
        explanation: 'После *need* в значении «нужно» идёт *to* + глагол: *I need to go*. После модальных глаголов '
            + '(*must*, *can*, *should*) частица *to* не нужна: *I must go*. О бытовых делах чаще говорят *need to* или *have to*, '
            + 'а *must* звучит как более сильная, личная необходимость. *Need* без *to* встречается в основном в отрицаниях '
            + 'и вопросах — в британском или формальном английском: *You needn’t go*.'
    }
];

function summary(score, total) {
    if (score === total) {
        return {
            title: 'Без ошибок.',
            text: 'Все варианты выбраны точно — эти конструкции у вас уже звучат естественно. '
                + 'На занятиях можно идти дальше: к беглости, оттенкам значения и живой разговорной речи.'
        };
    }
    if (score >= total - 2) {
        return {
            title: 'Хороший результат.',
            text: 'Ошибки здесь — типичные кальки с русского, и их легко поправить, когда понимаешь, откуда они берутся. '
                + 'Загляните в разбор ниже.'
        };
    }
    return {
        title: 'Эти фразы часто путают.',
        text: 'Особенно когда переводишь с русского дословно — это нормальная точка старта. '
            + 'В разборе ниже видно, как устроена каждая конструкция.'
    };
}

document.querySelectorAll('[data-quiz="natural-english"]').forEach(root => {
    createQuiz(root, { questions, summary });
});
