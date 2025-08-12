def call() {
    return [
        'graphql': [
            url: 'https://github.com/RoyalAholdDelhaize/eu-digital-graphql',
            branch: 'develop',
            path: 'pipeline/graphql-service'
        ],
        'store': [
            url: 'https://github.com/RoyalAholdDelhaize/eu-digital-fe-stores',
            branch: 'develop',
            path: 'pipeline/store-service'
        ],
        'bloomreach': [
            url: 'https://github.com/RoyalAholdDelhaize/eu-digital-bloomreach-cms',
            branch: 'develop',
            path: 'pipeline/bloomreach-service'
        ]
    ]
}

