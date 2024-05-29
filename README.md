# Projeto de Computação Distribuída

Este projeto foi desenvolvido no âmbito da Unidade Curricular **Computação Paralela e Distribuída** do 2º semestre do 3º ano da **Licenciatura em Engenharia Informática e Computação (LEIC)** da **Faculdade de Engenharia da Universidade do Porto (FEUP)**, no ano letivo 2023/2024.

## Elementos do Grupo

- Manuel Ramos Leite Carvalho Neto - 202108744
- Matilde Isabel da Silva Simões - 202108782
- Pedro Vidal Marcelino - 202108754

## Intruções de Execução

Para iniciar o servidor (`Server`), é necessário fornecer, por esta ordem, o número da porta, o nome do ficheiro da base de dados (que deve ter formato *.csv*), o modo de *matchmaking* (*0* para *simple mode* ou *1* para *rank mode*) e o número de jogadores por jogo.

Podemos considerar como exemplo `java Server 8000 database.csv 0 2`.

Para correr cada cliente (`Client`), é necessário atribuir o *hostname* e o número da porta, respetivamente, de modo a estabelecer uma ligação ao servidor anteriormente ligado.

Neste caso, podemos considerar como exemplo `java Client localhost 8000`.

## Apresentação do Projeto

O trabalho tem como objetivo a criação de um sistema cliente-servidor, utilizando *sockets* TCP e a linguagem de programação Java.

### Autenticação

Quando um cliente se liga ao servidor, é iniciado um processo de autenticação numa nova *thread* virtual criada para o efeito. É apresentado um menu com as opções de *login* e registo e, em ambos os casos, o cliente tem que fornecer o seu *username* e a respetiva palavra-passe.

No registo, é criado um novo jogador com os dados introduzidos, que posteriormente será guardado na base de dados, sendo utilizado o algoritmo *SHA-256* para encriptar as palavras-passe. No *login*, apenas se verifica se as credenciais fornecidas correspondem a algum registo já realizado. 

Assim que o processo de autenticação estiver completo, é associado um jogador ao *socket*, tornando-o num cliente.

Para evitar clientes lentos, cada cliente corre numa *thread* virtual própria e é-lhe pedida toda a informação antes de se consultar a base de dados, permitindo que todos os clientes consigam interagir com o servidor, mesmo que um cliente demore mais tempo do que o esperado.

### Fila de Espera

Após a autenticação ser bem-sucedida, o cliente é colocado numa fila de espera até o servidor ser capaz de criar um jogo com *n* jogadores, sendo o valor de *n* passado anteriormente como argumento da linha de comandos (ao iniciar o servidor).

É fornecida ao cliente a informação de que se encontra na fila de espera e o seu *ranking*, bem como o respetivo *token* que permitirá fazer a reconexão, caso a ligação falhe.

De 30 em 30 segundos, verifica-se se o cliente ainda se mantém ativo. Se o cliente perder a conexão, tem até à próxima verificação para voltar ao seu lugar na fila de espera. Numa tentativa de reconexão, é pedido o *token* atribuído anteriormente e, se for introduzido corretamente, o cliente entrará novamente na fila, para a posição em que se encontrava antes de perder a conexão. Caso contrário, o cliente terá que se autenticar novamente, indo para o final da fila.

Se um cliente estiver na fila de espera e tentar reconectar-se, o *socket* que estava na fila de espera é fechado, enquanto o *socket* da reconexão é o que reentra na posição correta da fila.

Não é permitido que um cliente que já está na fila volte a autenticar-se através de outro *socket*.

### Modos de *Matchmaking*

Existem dois modos de *matchmaking*: o *simple mode* e o *rank mode*. 

No *simple mode*, o servidor agrupa os clientes por ordem de chegada à fila de espera, criando cada jogo com os primeiros *n* clientes na fila.

No *rank mode*, o servidor agrupa os clientes por *ranking*, com uma diferença máxima entre *rankings* que começa em 5 e é incrementada de 5 a cada minuto. Assim, o aumento da tolerância ao longo do tempo permite que jogadores com maior *ranking* joguem com jogadores com menor *ranking* (e vice-versa), tentando evitar que os clientes passem muito tempo na fila de espera se não houver imediatamente outros clientes com *ranking* semelhante para iniciar um novo jogo.

### Jogo

Cada jogo corre numa *thread* virtual criada especificamente para o mesmo.

Quando o jogo começa, é enviada para o *socket* de todos os clientes a informação da equipa que faz parte desse jogo.

O jogo chama-se *TypeRacer* e o objetivo é escrever, no menor tempo possível, uma frase escolhida aleatoriamente (igual para todos os jogadores). Enquanto a frase submetida não corresponder à frase objetivo, é pedido ao jogador que volte a tentar.

No final da jogada de cada cliente, é indicado o tempo que demorou a escrever a frase e, depois de todos os clientes jogarem, são mostrados os resultados da partida, com os tempos de cada jogador e os lugares em que ficaram.

Se um jogador perder a ligação durante o jogo, assume-se que esse jogador se desconectou, de maneira a não deixar os outros jogadores à espera.

No final de cada partida, é atualizado o *ranking* de cada jogador, atribuindo *n-1* pontos ao jogador que ficou em primeiro lugar, *n-2* ao jogador que ficou em segundo lugar, e assim sucessivamente, sendo também atualizada a base de dados com os novos *rankings*.

### Sequência de Jogos

Depois de se conhecerem os resultados, é, então, perguntado a cada jogador se deseja jogar novamente.

Se a resposta for **não**, o *socket* do cliente é fechado, terminando a ligação.

Se a resposta for **sim**, o cliente será colocado de volta na fila de espera, com um novo *token* e com o ranking atualizado, sem necessitar de passar outra vez pelo processo da autenticação.

## Exemplo de execução

### *Simple Matchmaking*

Ligar o terminal do servidor: `java Server 8000 database.csv 0 2`.

![initial_server](/images/initial_server.png)

Ligar 3 clientes: `java Client localhost 8000`.

![open_3_clients](/images/open_3_clients.png)

Registar o Client1.

![register_client1](/images/register_client1.png)

Autenticar o Client2.

![login_client2](/images/login_client2.png)

Autenticar o Client3.

![login_client3](/images/login_client3.png)

A fila de espera continha o Client1 e o Client2 por se terem autenticado primeiro. Assim, como o servidor está em *simple mode*, criou um jogo com esses mesmos clientes.

![playing_client1](/images/playing_client1.png)
![playing_client2](/images/playing_client2.png)

O Client3, como foi o último a autenticar-se, ficará na fila de espera até chegar um novo cliente.

Depois de mostrar os resultados do jogo, é perguntado a cada jogador se quer jogar outra vez.
O Client1, que perdeu, não quer jogar outra vez, pelo que o seu *socket* é fechado.

![playAgain_client1](/images/playAgain_client1.png)

O Client2, que ganhou, vai querer jogar outra vez.

![playAgain_client2](/images/playAgain_client2.png)

Como o Client3 ainda estava à espera na fila, vão, então, jogar o Client2 e o Client3.

![new_game](/images/new_game_client2_client3.png)

Podemos observar que o Client2 no primeiro jogo tinha *ranking* 14 e, após ganhar, ficou com *ranking* 15, por ter ficado em primeiro, num jogo com dois jogadores.

### *Rank Matchmaking*

Abrir o terminal, ligar e autenticar os 3 clientes, como mostrado anteriormente, mas em *rank mode*.
Podemos observar que a diferença máxima de *ranking* começa em 5.

![server_rank](/images/server_rank.png)

Estão os 3 clientes na fila de espera, pois nenhum par de clientes tem diferença de *ranking* igual ou menor do que 5.

![diff_ranking](/images/diff_ranking.png)

Enquanto os 3 clientes esperavam na fila, o Client2 perdeu a sua ligação, tendo-se reconectado momento depois, mantendo, assim, a sua posição na fila (número 2).

![reconnect](/images/reconnect.png)

Com o passar do tempo, a diferença de *ranking* foi aumentada. Quando passou para 10, já permitiu o par Client1 e Client3 jogar, permanencendo o Client2 na fila.

![playing_rank](/images/playing_rank.png)

Enquanto o jogo prosseguia, podemos observar que a diferença de *ranking* continuou a aumentar por o Client2 permanecer na fila de espera. Assim, quando entrar um novo cliente na fila, o Client2 já não precisará de esperar mais.

![updated_diff_ranking](/images/updated_diff_ranking.png)
