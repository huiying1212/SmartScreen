Investigating Context-Aware Collab orative Text Entr y on
Smartphones using Large Language Mo dels
# Weihao Chen
Department of Computer Science and
Te chnology
Tsinghua University
Beijing, China
chenwh20@mails.tsinghua.e du.cn
**Yuanchun Shi**
Department of Computer Science and
Te chnology
Tsinghua University
Beijing, China
Qinghai University
Xining, Qinghai, China
shiyc@tsinghua.e du.cn
**Yukun Wang**
Department of Computer Science and
Te chnology
Tsinghua University
Beijing, China
wang- yk21@mails.tsinghua.e du.cn
**Weinan Shi**
*
Department of Computer Science and
Te chnology
Tsinghua University
Beijing, China
swn@tsinghua.e du.cn
**Meizhu Chen**
Scho ol of Archite cture
Tsinghua University
Beijing, China
cmz23@mails.tsinghua.e du.cn
**Cheng Gao**
Department of Computer Science and
Te chnology
Tsinghua University
Beijing, China
gao c24@mails.tsinghua.e du.cn
**Yu Mei**
Department of Computer Science and
Te chnology
Tsinghua University
Beijing, China
meiy24@mails.tsinghua.e du.cn
**Yeshuang Zhu**
Pattern Re cognition Center, WeChat
AI
Tencent Inc.
Beijing, China
yshzhu@tencent.com
**Jinchao Zhang**
Pattern Re cognition Center, WeChat
AI
Tencent Inc.
Beijing, China
dayerzhang@tencent.com
**Chun Yu**
Department of Computer Science and
Te chnology
Tsinghua University
Beijing, China
chunyu@tsinghua.e du.cn

## Abstract
Text entr y is a fundamental and ubiquitous task, but users often
face challenges such as situational impairments or di˝culties in
sentence formulation. Motivate d by this, we explore the p otential
of large language mo dels (LLMs) to assist with text entr y in real-
world contexts. We prop ose a collab orative smartphone-base d text
entr y system, CATIA, that leverages LLMs to provide text sugges-
tions base d on contextual factors, including scre en content, time,
lo cation, activity, and more. In a 7-day in-the-wild study with 36
participants, the system o˛ere d appropriate text suggestions in
over 80% of cases. Users exhibite d di˛erent collab orative b ehaviors
dep ending on whether they were comp osing text for interp ersonal
communication or information ser vices. Additionally, the relevance
*
Corresp onding author.
This work is license d under a Creative Commons Attribution 4.0 International License.
CHI '25, Yokohama, Japan
©
2025 Copyright held by the owner/author(s).
ACM ISBN 979-8-4007-1394-1/25/04
https://doi.org/10.1145/3706598.3713944
of contextual factors b eyond scre en content varie d across scenarios.
We identi˙e d two distinct mental mo dels: AI as a supp ortive facil-
itator or as a more e qual collab orator. These ˙ndings outline the
design space for human-AI collab orative text entr y on smartphones.
CCS Concepts
‹
Human-centere d computing
!
Empirical studies in HCI
;
Ubiquitous and mobile computing
.
Key words
Human-AI Collab oration, Text Entr y, Context-aware Computing,
Smartphones, Large Language Mo dels, In-the-wild Study
ACM Reference Format:
**Weihao Chen**, Yuanchun Shi, Yukun Wang, Weinan Shi, Meizhu Chen,
**Cheng Gao**, Yu Mei, Yeshuang Zhu, Jinchao Zhang, and Chun Yu. 2025.
Investigating Context-Aware Collab orative Text Entr y on Smartphones
using Large Language Mo dels. In
CHI Conference on Human Factors in
Computing Systems (CHI '25), April 26May 01, 2025, Yokohama, Japan .
ACM,
New York, N Y, USA, 20 pages. https://doi.org/10.1145/3706598.3713944
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
Figure 1: Mobile text entr y b ehaviors exist in the context of smartphone usage. For example, a user may share read news in an
instant messaging app, or go to a shopping app to search for a favorite pro duct after se eing it. We explore a text-suggestion AI
that utilizes contextual information on device to infer the user's input intention and suggest texts.
1 Intro duction
Text entr y is an essential part of ever yday smartphone usage, sup-
p orting diverse activities such as communication, information re-
trieval, and note-taking [
33
]. However, text entr y in real-world sce-
narios p oses signi˙cant challenges [
30
]. For instance, situational im-
pairments (e.g., walking or driving) can make typing inconvenient
[
23
], while heav y text input (e.g., comp osing lengthy resp onses)
may increase cognitive load, leading to di˝culties in formulating
coherent sentences [44, 45].
We envision a
human-AI collab orative text entr y
mo del,
where AI leverages contextual cues to provide relevant input sug-
gestions, while users re˙ne or adapt these suggestions base d on
situational ne e ds. Sp e ci˙cally, we aim to investigate the p oten-
tial of large language mo dels (LLMs) in providing this pre dictive
assistance.
The feasibility of this approach stems from the obser vation that,
in many cases, a user's input or underlying intent for text entr y
can b e inferre d from contextual information capture d by the smart-
phone [
16
,
28
]. As illustrate d in Figure 1, a scenario may involve a
user summarizing a news article from one app and sharing it with
friends via an instant messaging app. Alternatively, a user could
b e reading ab out a pro duct in an app and then want to search for
relate d items in a shopping app.
More over, with re cent advances, large language mo dels (LLMs)
have demonstrate d the ability to generate high-quality text base d
on the given context [
9
,
17
,
57
,
66
]. Due to their ˚exibility in input
and output, as well as their generality across a wide range of tasks,
LLM-driven AI assistants have the p otential to b e come end-users'
text entr y agents to tackle op en and complex input tasks [
8
]. In this
collab orative mo del, human-to-AI communication can b e divide d
into two complementar y channels: active expression, where users
intentionally typ e or provide dire ct input to the AI, and implicit
communication, where users convey through data derive d from
regular smartphone use [
6
,
48
,
49
]. Users can leverage context to
re duce their input burden while also actively providing additional
instructions to re˙ne the AI's suggestions with minimal e˛ort.
The challenge, however, lies in the fact that real-world collab-
oration b etwe en end-users and LLM-driven AI extends b eyond
traditional typing, as it involves a more complex dynamic than
simple end-to-end pre dictions. When given inaccurate instructions
or incomplete context, LLM suggestions may not re˚e ct the user's
true intent [
55
,
67
]. These suggestions might b e p erceive d either
as errors or as additional sources of inspiration. More over, users
may adapt their b ehavior base d on the context and p erformance of
the LLM, and their initial interaction intent could shift over time
[
50
,
53
]. This complexity is di˝cult to pre dict and understand in
advance and could signi˙cantly impact design de cisions.
Existing research has explore d the integration of LLMs with end-
user devices to provide context-aware text suggestions for various
scenarios, such as automatic form ˙lling on PCs [
4
], and mobile
blogging on smart glasses [
10
]. Commercial practices (e.g., Ap-
ple Intelligence [3]) have similarly explore d integrating on-device
contextual information into user applications, particularly on smart-
phones. Despite these advances in sp e ci˙c, pre de˙ne d scenarios, a
comprehensive understanding of how diverse contextual factors
and user ne e ds interact with LLM capabilities in op en-ende d, real-
world environments remains largely unexplore d. Furthermore, the
way users p erceive and exp e ct AI systems with human-like ca-
pabilities to collab orate in such real-world scenarios has yet to
b e investigate d. Empirical insights from such investigations can
inform the design of ubiquitous human-AI collab oration systems.
Motivate d by these gaps, we prop ose a research prototyp e sys-
tem: a smart phone-base d, context-aware text input assistant (CA-
TIA) using LLMs. CATIA leverages a wide range of contextual
factors, such as scre en content, time, lo cation, and activity, to pro-
vide p ersonalize d text suggestions tailore d to sp e ci˙c text entr y
˙elds. In addition, the system facilitates collab orative re˙nement
of suggestions by users. This system is designe d to supp ort the
investigation of human-AI collab oration in real-world text input
scenarios.
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
To understand how users interact with CATIA in natural settings,
we conducte d a 7-day in-the-wild study involving 36 participants.
The study is guide d by the following research questions to provide
b oth quantitative and qualitative insights:

RQ1: In what scenarios do users engage with the system, and
how do es it p erform in these contexts?

RQ2: Why do users cho ose to collab orate with the system rather
than simply accepting its suggestions?

RQ3: How do o˛-scre en contextual factors (e.g., time, lo cation,
and activity) contribute to the pre diction of user input?

RQ4: How do users p erceive the e˛e ctiveness of di˛erent LLMs?
The results show that users accepte d the system's suggeste d
text in 82.36% of cases, mainly for interp ersonal communication
and ser vice-oriente d tasks. For the remaining cases, users typically
adjuste d the suggestions rather than retyping. The system consis-
tently re quire d scre en text or text ˙eld information (mainly from
the ˙nal scre en), but in some instances, contextual factors such as
lo cation, date, time, calendar, and activity provide d valuable assis-
tance for quick text suggestions. Our evaluation of various LLMs
reveale d that smaller, faster, and more cost-e˛e ctive mo dels have
the p otential to achieve results comparable to larger mo dels.
Base d on these ˙ndings, we prop ose a design space for human-
AI collab orative text entr y on smartphones, identifying two key
mental mo dels: in straightfor ward tasks like retrieval and quick
replies, AI functions as a
supp ortive, non-intrusive facilitator
;
in complex, evolving scenarios such as so cial interactions, AI acts
as an
e qual collab orator
o˛ering diverse inspirations. We discuss
design choices aligne d with these mo dels.
In conclusion, our study provides empirical evidence of the e˛e c-
tiveness of context-aware text input and emphasizes the imp ortance
of tailoring AI assistance to sp e ci˙c tasks. It highlights the syner-
gistic interplay b etwe en contextual information, LLM knowle dge,
and user knowle dge in the collab orative pro cess. Our work con-
tributes to the future design and development of more adaptive and
contextually aware human-AI collab oration systems.
2 Relate d Work
Our research fo cuses on enhancing mobile text entr y by integrating
smartphone context and large language mo dels (LLMs) for accurate
text suggestions. This se ction reviews existing works in mobile
text entr y suggestions, intro duces a novel p ersp e ctive on text input
b ehavior through smartphone usage context, and discusses the
synergy b etwe en LLMs and context-aware applications.
2.1 Mobile Text Entr y Suggestions
Text entr y on smartphones, a re cognize d challenge [
31
,
38
,
58
], has
b e en the fo cus of extensive research. E˛orts to improve typing
p erformance have addresse d issues like the fat ˙nger problem,
limite d scre en real estate, and tactile fe e dback absence [
31
,
38
,
58
].
Prop ose d solutions include optimize d keyb oard designs (e.g., [
7
,
43
,
65
]), error corre ction or text pre diction me chanisms (e.g., [
19
,
59
,
62
]), etc., aiming to enhance typing sp e e d, accuracy, and overall
user exp erience [30, 38].
Text entr y suggestions in particular, aim to re duce interaction
costs through pre dictive completions [
21
,
47
]. Commercial smart-
phone keyb oards utilize such te chniques, primarily leveraging lin-
guistic re dundancy via statistical language mo dels [
24
,
30
]. How-
ever, these metho ds typically overlo ok the role of non-linguistic
context in optimizing text suggestions.
Conversely, in search tasks, leveraging context information to
ease quer y input has b e en a fo cus [
11
], such as using lo cation
and time for quer y suggestions [
28
] or enhancing app search with
temp oral b ehavior and app usage data [
1
]. Numerous market apps
employ lo cation data for search b ox suggestions. These works target
accurate item retrieval intents rather than general text input tasks.
Our research aims to merge these approaches, leveraging a rich
array of device context information to enhance text suggestions
across various smartphone input ˙elds.
2.2 Text Entr y in the Context of Smartphone
Usage
Rather than fo cusing on well-de˙ne d isolate d text input tasks as in
previous research, we delve into human text input b ehavior from the
p ersp e ctive of smartphone usage [
16
,
28
]. For example, contextual
information from the phone can hint at probable search goals. Lo cal
search b ehaviors and queries users input dep end on lo cation, time,
and so cial context [
54
]. Search topics users input into di˛erent apps
are relate d to the apps' functions [
12
]. Furthermore, Toby et al. [
33
]
study various text entr y b ehaviors within the realm of smartphone
app usage. They discover that the typ e of text entere d in di˛erent
apps correlates with the app's primar y function. Compare d to non-
text entr y sessions, text entr y sessions involve more apps instead
of just one, and users often avoid copy-pasting, opting instead for
retyping or other convenient sharing metho ds like scre enshots for
interp ersonal communication or data transfer, as rep orte d in their
study. This underscores that text input is merely a means to ful˙ll
users' higher-level intentions.
Yet, the underlying motives of text input b ehaviors and their ap-
plication in enhancing text input te chnologies remain unexplore d.
While Bemmann et al. prop ose a metho d for colle cting richer key-
b oard logs using Android APIs and categorizing input motives
base d on input UI metadata [
5
], their work do es not analyze user
b ehavior or o˛er guidance on how these results can assist in text in-
put. In contrast, our work is the ˙rst to combine a broader range of
contextual smartphone factors in an op en-ende d scenario, and use
LLMs to infer user intentions from real-time context. This enables
our system to not only capture input motives but also guide intelli-
gent text generation, providing p ersonalize d suggestions within a
collab orative human-AI framework.
2.3 Large Language Mo dels for Context-Aware
Applications
Generative language mo dels calculate the probability of text se-
quences and generate the most likely subse quent texts base d on
provide d input. As transformer-base d [
57
] language mo dels like
ChatGPT [
41
] increase in scale [
29
], they exhibit in-context learning
capabilities, where they can learn new tasks via textual prompting
without changing mo del parameters [
9
,
17
]. This ability transcends
traditional NLP tasks and generalizes to more complex challenges.
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
By converting information from di˛erent sources and mo dalities
into descriptive text within prompts, LLMs can demonstrate strong
context comprehension, supp orting asp e cts like p erception, reason-
ing, task planning, and exe cution [8, 25, 39, 46, 51].
LLMs enable new p ossibilities for context-aware applications
that adapt ser vices base d on user context [
15
]. These applications
often struggle with diverse and unforese en scenarios b e cause devel-
op ers cannot pre de˙ne all p otential user contexts [
56
]. Unlike ma-
chines, which represent contexts in a structure d manner, humans
convey and understand it through natural language, b ene˙ting
from its p ower and ˚exibility [
13
]. Thus, LLMs can express op en-
ende d contexts in natural language and infer implicit information
by leveraging their emb e dde d general knowle dge.
Several studies have explore d using context to provide text ser-
vices with LLMs. Some have leverage d external environment in-
formation sense d by devices. For example, PANDALens utilizes
multimo dal data (e.g., ˙rst-p erson view, lo cation, time, audi o) from
smart glasses to provide passage-level text suggestions for auto-
blogging in travel scenarios [
10
]. Others have fo cuse d on UI-base d
information, such as automatic form ˙lling on desktops using web
content and text ˙eld descriptions [
4
]. Notably, some studies have
investigate d how LLMs can analyze and utilize smartphone GUIs,
such as for accessibility tasks like hint-text pre diction [
37
] and for
GUI testing to generate simulate d user input [14, 36].
However, no studies have fo cuse d on context-aware text entr y
on smartphones without pre de˙ning the user's scenario or task.
Our research ˙lls this gap by combining device-p erceivable context
information on interface content, physical
context, and other addressing the collab orative nature
of user interaction with LLMs in an op en-ende d context.
3 CATIA: Context-Aware Text Input Assistant
Text entr y o ccurs within the broader context of users engaging in
daily activities on their smartphones. To explore how using large
language mo dels (LLMs) with the contextual information on de-
vices can aid in inferring input text, we designe d and implemente d
CATIA, a Context-Aware Text Input Assistant. The design of CATIA
takes into account the following factors.
Human-AI collab orative text entr y.
Existing text entr y meth-
o ds typically involve a p erson actively expressing thoughts through
typing or sp eaking. This pro cess grants users more control but re-
quires greater e˛ort, such as pre cise and complete articulation. In
contrast, AI assistants generating text using contextual information
left by users during device usage represents a more implicit form
of expression. Here, users are relieve d from the burden of active
expression, but the assistant might not always accurately guess
the user's intentions. We b elieve that an ideal approach is a combi-
nation of b oth: the user's active expression and other contextual
information complement each other, with b oth user and assistant
collab oratively generating the input text [
48
]. This is not a replace-
ment for existing text entr y metho ds, but rather an enhancement
in scenarios where the context is su˝cient to infer text, aiming to
re duce the input burden.
Comprehensible interface.
Most existing commercial input
metho ds include enhancements like auto-completion and error cor-
re ction, but primarily consider the context of characters already
typ e d in the text b ox. In contrast, CATIA considers more compre-
hensive factors such as time, lo cation, activity, app, and scre en
content, o˛ering text suggestions di˛erent from existing to ols. This
re quires users to develop a mental mo del b eyond conventional in-
put metho ds, understanding CATIA's actions as a more human-like
assistant. Therefore, we b elieve it is ne cessar y to display the con-
textual information CATIA relies on and provide an explanation
for each suggestion. Users may cho ose to ignore this information
after b e coming familiar with the system, but its presence is crucial
for providing transparency, establishing trust in the AI assistant,
and re ducing metacognitive demands, particularly in complex or
dynamic contexts [2, 13, 34, 35, 53].
Base d on these considerations, we implemente d CATIA as a
collab orative text suggestion system with an understandable in-
terface on Android smartphones. It leverages LLMs to o˛er text
suggestions for the target text ˙eld base d on context information
colle cte d in the short term and allows users to provide additional
instructions to guide the generation of more appropriate text. In the
following se ction, we will ˙rst intro duce CATIA's interface and the
collab orative interaction work˚ow b etwe en the user and CATIA.
We will then detail the system design, including the device contex-
tual information colle cte d by CATIA and the metho d of generating
suggestions using LLMs. P lease refer to App endix A for example
use cases, and App endix B for implementation details.
3.1 Collab orative Interaction Work˚ow
The interaction b etwe en the user and CATIA, referre d to as a ses-
sion, involves thre e steps: initiating the suggestion pro cess, collab-
orating on the text, and con˙rming the ˙nal text. We illustrate this
pro cess with an example as shown in Figure 2.
When a user desires suggestions, they long-press the CATIA
˚oating button on the page containing the input text b ox. The
suggestion panel is a draggable ˚oating overlay that can b e move d
up and down, allowing the user to fre ely adjust it to view the content
b elow. The assistant simultane ously colle cts contextual information
from the device in the background for text suggestions.
The suggestion panel displays four se ctions from top to b ottom:
a brief over view of the context use d by the assistant, the assistant's
guess of the user's intention, several suggeste d texts, and a text b ox
for the user to e dit input text or provide instructions to the assistant.
The context over view brie˚y intro duces the information capture d
by the assistant. The guesse d intention app ears when the user
sele cts a suggeste d text, shown as natural language re˚e cting the
assistant's interpretation of the user's underlying motivation. As
previously discusse d, these two parts are designe d to make CATIA's
suggestions more understandable to the user. The panel displays
up to four suggeste d texts, which are dynamically presente d to the
user character by character to provide imme diate fe e dback.
In the text input ˙eld at the suggestion panel's b ottom, users can
mo dify the text displaye d on scre en. Users can click on a suggeste d
text for automatic copying to the
Text
tab for e diting, or manually
enter text if unsatis˙e d with the suggestions. To guide the assistant
for new suggestions, users enter instructions in the
Instruct
tab
and click con˙rm for regeneration. Notably, when e diting text in
the panel's text b ox, the user uses the existing input metho ds on
the phone, allowing them to use familiar input metho ds, including
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
Figure 2: Interaction work˚ow of CATIA. A complete session involves thre e steps: capturing context, reviewing and collab orating
on suggeste d results, and con˙rming the ˙nal text.
typing and voice input. Additionally, the panel facilitates voice
input through a press-and-hold voice button for convenience.
After text e diting in the
Text
tab, users can click
confirm
for
the assistant to copy text to the scre en's p ending input ˙eld, or
click
cancel
to end the interaction.
3.2 Contextual Information
Up on user activation, CATIA gathers device contextual information
in the background to inform text suggestions. This information
includes date and time, day of the we ek, lo cation, activity, conne cte d
Blueto oth devices and WiFi, calendar events, scre en content, and
the text input ˙eld to b e ˙lle d.
Sp e ci˙cally, The lo cation is a human-readable place name, con-
verte d from GPS co ordinates. The activity, re cognize d by our cus-
tom de ep learning mo del, is categorize d as still, walking, running,
cycling, or others. Blueto oth and WiFi data provide details on the
typ e and name of currently conne cte d devices and access p oints.
Calendar events include the nearest thre e past and thre e future
entries, relative to the current time. The text input ˙eld, where the
suggestion panel is activate d, includes thre e key descriptors: the
source app name, a lab el indicating its intende d function, and the
pre-existing text content.
Di˛erent from other contextual information is the colle ction of
scre en content. The system continuously compiles a two-minute
queue of re cent scre en interfaces in the background. Scre en con-
tent, aggregate d from all visible text via Android's Accessibility
Ser vice API, fe e ds into this colle ction. Exceptionally, for instant
messaging apps' chat scre ens, we employ a re cognition algorithm
that structures page contents into chat lists pairing senders with
messages, b eyond mere text enumeration. This approach aids LLMs
in capturing key chat information more e˛e ctively.
3.3 Generation of Text Suggestions
CATIA prompts LLMs to generate text suggestions. In this study,
we use a general, widely-adopte d metho d,
avoiding
sp e ci˙c as-
sumptions ab out particular text input tasks due to the challenge
of pre dicting each end-user's unique scenario with impre cise prior
knowle dge. This strategy enhances the broader a pplicability of our

## Conclusions and provides a foundation for future re˙nements.
The generation pro cess is illustrate d in Figure 3. Each sugges-
tion includes an intention description that explains the p ossible
motivation b ehind the re commendation. We use a chain-of-thought
approach [
61
] to make the LLM se quentially pro duce the intention
and the corresp onding suggestion. This token-generation pro cess
facilitates more consistent and interpretable reasoning.
Sp e ci˙cally, we use a GPT-4 Turb o mo del
gpt-4-1106-preview
1
from Op enAI's chat completions API
2
. The prompt use d by CATIA
for the initial suggestion primarily contains thre e parts: task descrip-
tion, input-output examples, and contextual input for this sugges-
tion. The task description mainly informs the LLM of its role as a text
suggestion assistant, intro duces the content and format of the con-
textual variables (such as
context.location
,
input_field.app
,
etc.), and re quests the LLM to think step-by-step and output sug-
gestions. The thinking steps instructe d for the LLM include thre e
stages. Firstly, to consider which contents in the contextual infor-
mation are relevant to the user's input b ehavior. This is b e cause
the input contextual information might b e abundant (esp e cially
the colle cte d scre en text), but not all of it is useful. Se condly, to
analyze the p ossible input intentions of the user, which could b e
multiple. This is b e cause solely relying on contextual information
might not uniquely determine what the user intends to express.
Finally, to generate a suggeste d text for each input intention. Under
the guidance of these thre e steps, the mo del is re quire d to output up
to four intention-suggestion pairs, sorte d in the order of what the
mo del considers more likely. After the task description, we include
several input-output examples in the prompt, all represente d in
JSON obje ct format. The last part of the prompt is the colle cte d
contextual information for a particular suggestion, represente d as
a JSON obje ct. P lease refer to App endix C.1 for the complete task
description part of this prompt.
1
GPT-4 Turb o: https://platform.op enai.com/do cs/mo dels/gpt- 4- and- gpt- 4- turb o
2
Op enAI chat completions API: https://platform.op enai.com/do cs/api- reference/chat
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
Figure 3: The generation pro cess of text suggestions.
The prompt for regenerating suggestions (i.e., when the user
inputs instructions on the panel and re quests regeneration) has the
same structure as the ab ove one and also contains thre e parts. The
di˛erence is that each input example also includes the user instruc-
tion entere d on the panel and the results of the previous suggestion.
Thus, the task description informs the LLM that the task is to regen-
erate suggestions, adds intro ductions to
user_instruction
and
last_output
, and re quests the LLM to think in two steps: to con-
sider the intentions base d on the provide d information, and then
output suggeste d text for each intention. P lease refer to App en-
dix C.2 for the complete task description.
3.4 Delay and User Exp erience
The most time-consuming step in CATIA's suggestion pro cess is
the invo cation of the LLM. To minimize user waiting time, the sys-
tem streams the text suggestions on the interface, displaying them
character by character as they are parse d from the LLM resp onse.
This allows users to start interacting with the suggestions as they
app ear. Base d on preliminar y testing, the
˙rst character time
(the
time from triggering the LLM call to displaying the ˙rst character
of the suggestion) typically ranges from 2 to 4 se conds.
It is worth noting that during the LLM invo cation delay, the
suggestion panel also simulates a streaming animation of the cap-
ture d context information (se e Figure 2), providing continuous
visual fe e dback to users and preventing a lagging exp erience. The
total time for a complete suggestion dep ends on the numb er of
suggestions and the length of the text. However, the actual delay
exp erience d by the user can also b e in˚uence d by factors such as
device p erformance and network communication time.
4 In-the-wild Study
To understand context-aware collab orative text entr y in real-world
scenarios, we conducte d a 7-day in-the-wild study to explore how
smartphone users interact with our system under natural condi-
tions. In line with the research questions (RQs) mentione d earlier,
our approach aime d to capture the following: (1)
real contextual data
,
encompassing valuable information that researchers cannot antici-
pate or construct; (2)
authentic user ne e ds
, by avoiding pre de˙ne d
tasks and supp orting ˚exible, p ersonalize d usage in op en-ende d sce-
narios; and (3)
in-situ collab orative interactions
 in real-world
contexts, users are b etter able to p erce ive and express their ne e ds
and engage in collab oration. Considering the p otential b ene˙ts,
ethical approval was obtaine d from the Arti˙cial Intelligence Ethics
Review Board at the authors' university, which is resp onsible for
evaluating AI-relate d human-subje cts research.
4.1 Participants
We re cruite d participants who were native Mandarin sp eakers, use d
Android smartphones, and rep orte d a high fre quency of text input
in their daily lives. Participants were fre e to withdraw from the
study at any time, and any data colle cte d from those who withdrew
were delete d after the study. A total of 36 participants (20 males, 16
females) who complete d the full study remaine d, age d b etwe en 18
and 27 (
"
=
20
ﬂ
89
,
( ˇ
=
2
ﬂ
38
). All participants were undergraduate
or graduate students from the same university in China. The CATIA
system interface was provide d in Chinese, and participants use d the
system in Chinese for text entr y during the study. All participants
signe d an informe d consent form and re ceive d comp ensation for
their participation.
4.2 Pro ce dure
Participants ˙rst attende d our pre-study brie˙ng session. We intro-
duce d the typ es of context information the assistant could colle ct
and its ability to provide text suggestions in any text b ox, but we did
not explain the underlying principles of how the assistant compute d
these suggestions. Then, participants installe d the CATIA app on
their own phones and learne d how to use the assistant under the
guidance of the exp erimenters. Data generate d during this learning
phase was not colle cte d. Once we con˙rme d that the participants
understo o d the assistant's functions and could use it corre ctly, we
informe d them that the 7-day in-the-wild study would b egin at
midnight the following day.
During the study p erio d, participants were re quire d to ke ep the
app running in the background and integrate it into their daily
smartphone activities. No sp e ci˙c tasks were assigne d, and partici-
pants were fre e to use the system as ne e de d. However, participants
were reminde d daily to engage with the system, ensuring consistent
usage and minimizing the risk of forgetting.
At the end of the study, we conducte d semi-structure d inter views
with participants via online voice, typically lasting no more than
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
20 minutes. The inter views fo cuse d on thre e main areas: (1) which
scenarios participants found the system useful or not useful, (2)
why participants felt the ne e d to collab orate (e dit or regenerate
text), and (3) how participants understo o d the system and what
their exp e ctations were. Finally, participants remove d the CATIA
app from their phones.
4.3 Data Colle ction
The mobile ser vice communicates with a remote ser ver resp onsible
for exe cuting the suggestion pro cess and returning the suggeste d

## Results. Ever y time the user engages with the assistant, the ser ver
logs the capture d context, suggestion results, and user interactions
within the session. We ˙lter out instances where users trigger and
close the assistant multiple times conse cutively within the same
text ˙eld without changes to the page content, as this indicates
improp er usage. Only the ˙nal instance is re corde d. All colle cte d
data is store d in text-only format, including the visible text on the
scre en accesse d via the accessibility API.
Data colle cte d during the study is store d on a de dicate d remote
ser ver, with access restricte d to a limite d set of research team mem-
b ers. If the participant closes the assistant panel through cancella-
tion (e.g., due to accidental touch), or if the session data is incom-
plete due to te chnical issues (e.g., unstable network conne ction),
the data from that session will not b e analyze d and will b e delete d
after the study to prote ct user privacy.
Participants were informe d of the data colle ction pro cess through
the consent form and the brie˙ng session. They understo o d that
data colle ction o ccurre d only when they triggere d the assistant, and
that they could cancel any session if they felt uncomfortable. They
were assure d that all data would b e anonymize d prior to publication.
The university's AI Ethics Review Board considere d these privacy
concerns and approve d the exp erimental pro ce dure.
4.4 Post-ho c Analysis
We prompte d the LLM to analyze two key asp e cts of the colle cte d
re cords: (1) the user's true intent b ehind each input, and (2) the key
contextual factors that contribute d to inferring the entere d text.
Although limite d than human exp ert analysis, using LLMs ensures
consistency
in applying the same standards (or biases) across diverse
scenarios, enabling more reliable comparative analysis [
26
]. Sp e cif-
ically, we use d the same GPT-4 Turb o mo del
gpt-4-1106-preview
as in CATIA, with the same prompt structure and similar explana-
tor y text. For each re cord, the contextual data colle cte d during
the study and the ˙nal ground truth provide d by the user were
considere d. For the complete task description, se e App endix C.3.
User Intent
. For each re cord, we provide d the colle cte d con-
textual information alongside the user's ˙nal input text, asking
the LLM to infer the user's true intent in natural language. After
obtaining the LLM's results for all re cords, two annotators (the
authors) indep endently reviewe d all inferre d intents and discusse d
their categorization criteria. Each annotator then annotate d the
data according to these criteria and resolve d any inconsistencies
through consensus. Results are describ e d in Se ction 5.1.1.
Key Contextual Factors
. The categorization of contextual fac-
tors followe d the variable de˙nitions in the suggestion generation
prompts. In particular, the scre en content was represente d as a
time-ordere d list variable
context.screen_content
, where each
element corresp onde d to a page and include d two attributes: page
typ e (either
chat
or
non-chat
, determine d by the page layout re cog-
nition algorithm) and page text content. The LLM was taske d with
determining which page typ es or content in the list were critical for
generating the suggestion. After analyzing all re cords, we quanti-
˙e d the o ccurrence of di˛erent factors and analyze d their in˚uence
on the generate d suggestions. Results are describ e d in Se ctions 5.1.2
and 5.3.
5 Results
We colle cte d a total of 2,505 re cords of valid interactions with
CATIA. In 2,063 cases (82.36%), users chose the system's suggestions
without making any manual mo di˙cat ions. Among these, in 1,893
cases (75.57%), the system provide d the sele cte d suggestion in its
initial round, without re quiring further regeneration. On average,
each session with the system laste d 32.47 se conds. Participants
interacte d with the system an average of 9.94 times p er day, totaling
approximately 5.38 minutes of daily interaction.
We identi˙e d two distinct patterns of user interaction with CA-
TIA, primarily categorize d into two major text entr y scenarios:
interp ersonal communication and ser vice-oriente d tasks. In this
se ction, we will explore the unique ˙ndings across these two scenar-
ios through the lens of four key research questions: usage scenarios,
user collab oration, o˛-scre en contextual factors, and the choice of
LLMs. All user texts in Chinese were translate d into English.
5.1 RQ1: Scenarios and Performance
5.1.1 Input Field Typ es and Intentions.
We analyze d the system
usage across di˛erent text input ˙elds, with the results summarize d
in Table 1. O verall, the majority of system-assiste d text entries were
use d in interp ersonal communication contexts, such as messages
and comments, with the remaining entries concentrate d on tasks
relate d to information ser vices, such as searches. In these two pri-
mar y scenarios, the numb er of times users dire ctly sele cte d the
system's suggestion without manual mo di˙cation was 81.85% for
interp ersonal communication and 87.08% for ser vice-oriente d tasks.
The numb er of corre ct suggestions re ceive d without the ne e d for
regeneration was 75.10% and 80%, resp e ctively. These ˙ndings indi-
cate that, in most cases, users were satis˙e d with the suggestions
provide d by the system.
The text input intentions obtaine d from the p ost-ho c analysis are
shown in Table 2. So cial text ˙elds exhibite d more diverse intents;
for example, message and comment typ es could corresp ond to
various so cial interaction b ehaviors. In contrast, the intentions for
ser vice-oriente d input were more closely aligne d with the function
of the text ˙eld.
So cial scenarios constitute d the majority of cases in the study,
which aligns with previous research indicating that most of users'
daily text input is entere d into communication apps [
5
,
33
]. In
inter views, 11 participants mentione d that the assistant's more
formal tone was ver y suitable for certain so cial situations, such
as communicating with elders or strangers, which is consistent
with [
20
]. They also appre ciate d the diversity of the suggeste d
texts, which were often more appropriate and comprehensive than
their own expressions, encouraging them to use the assistant's
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
Table 1: Statistics of total sessions, corre ct suggestions, average length, average manual e dit distance, and example apps by
input ˙eld typ e, group e d into interp ersonal communication and ser vice-oriente d tasks.
Field Typ e Total
Corre ct
Avg. Length Avg. Edit Dist. Example Apps
Interp ersonal Communication
Message 2,099 1,713 22.02 2.10 Weixin, QQ, WeCom, Douyin, Taobao
Comment 164 140 23.18 1.28 Bilibili, Weixin, Douyin, Xiaohongshu, Zhihu
Post 1 0 15.00 33 Weixin
Note 1 1 12.00 0 Meituan
Total: 2265 (90.42%) Corre ct: 1854 (81.85%) Avg. Edit Dist.: 2.06
Ser vice-oriente d Tasks
Search 229 200 8.90 0.90
Taobao, Pinduo duo, Bilibili, Browser, Ele.me, Amap
Form 7 5 8.14 1.71 Weixin
Chatb ot 4 4 73.00 0 Wenxin Yiyan (ERNIE Bot), PaiPai Assistant
Total: 240 (9.58%) Corre ct: 209 (87.08%) Avg. Edit Dist.: 0.91
Table 2: Categories of input intentions, corresp onding input ˙eld typ es, and their examples.
Categor y Field Typ e Example
Interp ersonal Communication
Share comment, p ost, message The user intends to share their music exp erience and re commend a song to a group.
Emotional comment, message
The user is resp onding to a group chat memb er Alice who mentione d that they are currently learning to
play a new hero, presumably in a game, and the user is o˛ering encouragement.
Inquire comment, search, message The user intends to inquire ab out the pro cess for ordering fruit and milk for the next day in a group.
P lan comment, message, chatb ot
The user intends to sche dule a time to participate in an exp eriment with Alice by resp onding to a message.
Reply comment, message
The user is resp onding to Alice's fe e dback on a do cument or presentation, indicating that they have made
the suggeste d changes and are asking for a review or if there are any further additions ne e de d.
Comment comment, message
The user intends to comment on a friend's p ost, sp e ci˙cally mentioning the p ost ab out the theme e ducation.
Gre etings comment, message
The user intends to intro duce themselves to the group and express a desire for future communication
regarding their graduation proje ct preparations.
Ser vice-oriente d Tasks
Shopping search The user intends to search for Nike sho es on a shopping app.
Vide o or Song
search
The user intends to search for vide os relate d to Statue of David after encountering a vide o or comment
ab out the topic.
Lo cation search The user intends to search for dire ctions or information ab out a building using a navigation app.
News search The user intends to search for re cent events or news relate d to McDonald's on an app.
Learning search, chatb ot
The user intends to search for information ab out the applications of QR de comp osition after previously
searching for relate d mathematical terms.
Command search, note The user intends to navigate to an app's homepage, likely for entertainment or information purp oses.
Practice chatb ot
The user intends to deliver a sp e e ch and is likely preparing or practicing the sp e e ch using an LLM app.
The sp e e ch emphasizes the imp ortance of literature in enriching the soul alongside the foundation of
scienti˙c knowle dge.
Alias search, form
The user is adding a new contact on a so cial app, p ossibly some one they re cently met or ne e d to get in
touch with for work or academic purp oses. The user is setting a nickname for the contact, which includes
the contact's name and a˝liation.
suggestions. Two participants state d that sometimes they didn't
know what to say, but the assistant provide d a go o d suggestion
that happ ene d to match their intentions. P5 mentione d: 
When
resp onding to a noti˙cation ab out an event, the assistant help e d me
by asking, `Where is this address? Could you send me the lo cation?'
and I realize d that I actually didn't know where the place was.
 Thre e
participants also mentione d that sometimes assistant resp onses
inconsistent with their own style in informal situations create d an
entertaining e˛e ct and were also welcome. For example, P22 said:

When commenting on p osts, it's quite amusing. Sometimes I can't
think of a comment, but the assistant's suggestion is unexp e cte dly
clever.

The remaining scenarios are mainly in search scenarios, rep-
resenting participants accessing information or ser vices through
queries. Eight participants also mentione d that they like d the as-
sistant's suggestion of search queries base d on their phone usage
histor y. Although the sp e ci˙c prop ortions of scenarios var y from
p erson to p erson, the typical usage patterns involve d here are of
reference signi˙cance.
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
5.1.2 Usage of Scre en Content.
The usage of scre en content was
determine d base d on the key contextual information analyze d in
Se ction 4.4. The most fre quently use d factors came from scre en
content and input ˙eld information. Within scre en content, page
typ e (chat or non-chat) and text content play the main roles. In the
input ˙eld, the lab el describing its function is the most imp ortant.
Other contextual factors are analyze d in se ction 5.3.
Figure 4: Distribution of sessions by oldest scre en use d for
text suggestion. Numb ers on the horizontal axis represent
the reverse order of the scre ens (i.e., how many scre ens back).
Across all sessions, the numb er of scre ens the system use d ranges
from 1 to 7, with an average numb er of 1.04 (
( ˇ
=
0
ﬂ
27
). We also
considere d the oldest scre en ne e de d for each session ( how many
scre ens back), and the distribution of the sessions is shown in
Figure 4. In most cases, the imp ortant information is containe d
within the last scre en. This is understandable in so cial scenarios,
as message chats, so cial me dia interactions, etc., are mostly fully
displaye d within one scre en.
Four participants mentione d that the assistant's ability to re-
memb er and summarize across scre ens can re duce the burden of
human input. Five participants mentione d that in situations where
the context is relatively consistent and matches their own intent,
the assistant's suggestions are ver y accurate, such as when all mes-
sages in a group chat are talking ab out the same topic, or when the
content they want to share is exactly the p ost they just saw.
5.2 RQ2: User Collab oration
User collab oration with the system o ccurre d in two phases: one in-
volve d providing additional instructions to regenerate suggestions,
and the other involve d manually mo difying the suggestions after
sele ction or retyping the text.
5.2.1 User Instructions for Regeneration.
296 sessions with regen-
eration attempts were colle cte d in the study, and a total of 354
instructions were prop ose d in these sessions, with each session
having 1-4 instances of instruction (
" 4 0=
=
1
ﬂ
20
,
( ˇ
=
0
ﬂ
52
).
Two annotators (the authors) reviewe d all the instructions pro-
vide d by users, discusse d, and categorize d them into these ˙ve
typ es:
Expressing Intention
: Brie˚y expressing the text's intent,
leading the assistant to return an expansion matching the intention;
Emphasizing Existing Key words
: Providing key words to fo cus
on corresp onding information in the context;
Providing New Key-
words
: Supplying key words to add information not present in the
context;
Giving Text Examples
: Dire ctly providing text examples;
Tone / Writing Style Adjustment
: Making sp e ci˙c re quests to
mo dify the tone, style, or other characteristics of the text. The ˙rst
thre e are instructions targeting intent, while the last two target
the text itself. Statistics and examples of each instruction typ e are
presente d in Table 3.
In di˛erent scenarios, users had var ying tolerance levels for the
quality of regenerate d suggestions. The average numb er of regen-
eration attempts in interp ersonal communication and information
ser vice tasks was 1.21 and 1.12, resp e ctively. In so cial scenarios,
users were generally more willing to sp end time interacting with
the assistant to achieve a more satisfactor y result through collab o-
ration. This was con˙rme d in participant inter views, where they
mentione d 
attempting multiple adjustments to the assistant's sug-
gestions
 when sending messages or p osting comments. However,
in faster-pace d tasks like searches, if the suggestions remaine d
inaccurate after a retr y, users tende d to resort to manual input.
5.2.2 Manual Edits.
All participants agre e d that, despite the ne e d
for manual e dits in some cases, the assistant's ability to provide
an initial draft for longer texts and so cial interactions re duce d the
burden of typing from scratch.
We calculate d the Levenshtein distance (e dit distance) for each
mo di˙e d re cord, measuring the minimum numb er of insertions,
deletions, or substitutions re quire d to transform a suggeste d text
into the ˙nal version [
32
]. The average e dit distance in interp ersonal
communication scenarios was 2.06, while in ser vice-oriente d tasks,
the average e dit distance was 0.91. This indicates that users made
relatively more e dits in so cial interaction scenarios.
We examine d 442 sessions where users made manual e dits to
categorize the typ es of e dits they p erforme d. Two annotators (the
authors) initially reviewe d the colle cte d contextual information,
including the last sele cte d text (if any) or all suggeste d texts (if none
were chosen), and the ˙nal con˙rme d text. They then discusse d
and establishe d classi˙cation criteria, and indep endently annotate d
all the data base d on these criteria. Any discrepancies in their
annotations were discusse d to reach a consensus.
We identi˙e d thre e major categories of manual e dits, each re˚e ct-
ing di˛erent typ es of user engagement with the system. Examples of
each typ e are presente d in Table 4. These categories provide a more
nuance d view of how users interact with the system's suggestions
and re˙ne them to suit their ne e ds.
The ˙rst categor y,
High-Quality Text with Minor Changes
,
re˚e cts cases where the system's suggestions were largely accurate,
and users primarily made small adjustments to re˙ne the tone or
remove re dundant information. Participants often found that the
suggestions capture d their intent but re quire d stylistic changes
to match their p ersonal communication style. For example, P14
mentione d, 
It's easy for p e ople to tell it's not me, like b eing to o
serious or the humor is o˛,
 indicating that minor mo di˙cations
were ne cessar y to make the text fe el more natural and aligne d with
the user's tone. In these cases, users value d the suggestions as a
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
Table 3: User instruction typ es, their p ercentages and examples.
User Instruction Typ es Examples
Expressing Intention (259, 73.16%) 
I want to help my classmate come up with a solution for a leave application
; 
Express fear

Emphasizing Existing Key words (38, 10.73%)

League of Legends
; 
Pop Mart
; 
Emergency department

Providing New Key words (13, 3.67%) 
Game
; 
Doll
; 
Rabbit
; 
Riemann sum

Giving Text Examples (19, 5.37%) 
To day is quite windy. Sure you want to bring badminton?
; 
OK

Tone / Writing Style Adjustment (25, 7.06%)

Within thre e words
; 
Be funnier
; 
Don't b e to o p olite

Table 4: Manual e dit typ es and examples categorize d by suggeste d text characteristics and user mo di˙cations. Gre en-highlighte d
text represents additions and re d-highlighte d text represents deletions.
A. High-Quality Text with Minor Changes (231, 52.26%)
A1. Re dundant Information (93, 21.04%)
: The suggeste d text accurately conveys the intent but includes some re dundant information, which the user
easily removes.

Mo di˙e d 
Bob,
you've worke d hard, health is the most imp ortant
; we'll handle the questionnaire
, you just rest well.
 to 
You've worke d hard, health is the
most imp ortant, you just rest well
.

Wante d to set a contact nickname and mo di˙e d 
Alice
- Dream Bo oster Activity

to
 Alice.

Wante d to search for a Q&A p ost and remove d 
relate d discussions
 in 
Nihilism
relate d discussions
.
A2: Style Di˛erences (138, 31.22%)
: The suggeste d text p erfe ctly matches the user's intent but di˛ers in style, punctuation, or emphasis. Minor
adjustments re˙ne the text to suit the user's preferences.

Mo di˙e d 
Oh, go o d reminder,
I'm a bit busy these days
, might write it later.
 to 
Oh, go o d reminder,
completely forgot
, might write it later.


Mo di˙e d 
The pre-defense me eting time at 11:30 am tomorrow is
 no problem
for me
. to 
Re ceive d,
no problem.

Was suggeste d 
I'm going to sle ep, hop efully, I'll fe el b etter when I wake up tomorrow.
 and manually entere d  Sle eping, hop e ever ything's ˙ne when I
wake up.
B. Reusable Text (158, 35.75%)
B1. Misaligne d Intention but Useful Structure (43, 9.73%)
: The suggeste d text may convey an opp osite intent, but the overall structure is useful and
re quires minimal changes.

Mo di˙e d 
Haha, we really have the mindset of the
young
but the lifestyle of the
old
 to 
Haha, we really have the mindset of the
old
but the lifestyle of the
young
.

Mo di˙e d 
If you have questions ab out the content in that picture,
I can tr y to explain.
 to 
If you have questions ab out the content in that picture,
there's nothing I can do ab out it
.

Mo di˙e d 
It
's
 snow
ing? It starte d so early, the weather changes so much.
 to 
It
also
 snow
e d a bit in Beijing yesterday, but just a little
.
B2. Partially Accurate Content (115, 26.02%)
: The suggeste d text captures part of the user's intent but includes some errone ous or incomplete
information. Most of the text is dire ctly usable with minor e dits.

Mo di˙e d 
I just che cke d the b onus calculation,
 no issues
.
 to 
No issues
on the science asso ciation's side.


Mo di˙e d 
Inde e d, the p ortability of the Steam De ck and PC is not as go o d as handheld consoles, but the gaming exp erience and graphics will b e
much
b etter
.

to 
Inde e d, the p ortability of the Steam De ck and PC is not as go o d as handheld consoles, but the gaming exp erience and graphics will b e
somewhat
b etter
.

Wante d to search for a tutorial and was suggeste d 
Ge oGebra Tutorial
, but manually entere d 
How to draw a parametric e quation using Ge oGebra
.
C. Text Re quiring Extensive Mo di˙cations (53, 11.99%)
The suggeste d text is not relevant to the user's intention, ne cessitating signi˙cant e dits.

Manually entere d 
Go o d morning
 to initiate a new interaction, but the suggeste d texts were all resp onses to the group chat histor y.

Manually entere d 
Isn't the exp e cte d outcome just a bachelor's thesis?
, but the suggeste d texts were all other comments ab out the thesis prop osal defense.

Wante d to search for a lo cation in the map app, but no relevant suggestions were provide d.
useful ˙rst draft but felt the ne e d to p ersonalize the output to b etter
re˚e ct their unique voice.
The se cond categor y,
Reusable Text
, involve d scenarios where
the system provide d a structurally or literally sound suggestion,
but the generate d text did not fully align with the user's intende d
meaning. While users reuse d the general framework of the sugges-
tion, they ne e de d to make more substantial content adjustments
to b etter capture their exact intent. P10 highlighte d this challenge,
stating, 
Most of the time, it cannot capture what I want to say in con-
versations with p e ers, as the context might not b e ver y relevant.
 This
was particularly evident in information ser vice tasks, where the
system's suggestions were close to the desire d output but re quire d
signi˙cant e dits to convey the pre cise message. In these cases, the
system help e d users by providing a starting p oint, but the ˙nal
expression of intent re quire d further re˙nement.
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
The third categor y,
Text Re quiring Extensive Mo di˙cations
,
o ccurre d when the system's suggestions were largely irrelevant to
the user's intent, re quiring major revisions or a complete rewrite
of the text. In these cases, b oth the form and content of the sugges-
tions misse d the user's exp e ctations, often due to misinterpreting
the context or failing to capture the user's intent altogether. For
example, P9 note d, 
After reading p osts and then searching for shop-
ping content, there is a lot of content in the p osts, and the assistant
fails to pre cisely capture what I want.
 Similarly, P16 commente d,

In multi-scenario situations, it tends to link completely unrelate d
scenarios together,
 indicating that in more complex, context-heav y
tasks, the system struggle d to pro duce relevant suggestions.
These ˙ndings suggest that while the system can act as a valuable
drafting to ol, the depth of user involvement varies signi˙cantly
base d on how well the suggestions align with the user's intent and
the complexity of the task.
5.3 RQ3: O˛-Scre en Contextual Factors
All participants re cognize d CATIA's ability to incorp orate contex-
tual information into its text suggestions. While the most fre quently
use d cues were on-scre en, o˛-scre en factors such as lo cation, date
and time, calendar events, day of the we ek, and user activity were
also imp ortant in sp e ci˙c scenarios, as illustrate d in Table 5.
Participants found o˛-scre en cues particularly useful in situa-
tions where they ne e de d to resp ond quickly without typing much.
For example, P18 mentione d: 
Once a classmate aske d me if I had ar-
rive d at the cafeteria. The assistant, using my lo cation near the librar y,
suggeste d resp onses like `On the way' or `P lease wait.' This allowe d
me to quickly reply even though I was riding a bike and couldn't typ e
easily.
 Similarly, cues such as the time of day or day of the we ek
were helpful in shaping resp onses that aligne d with daily sche dules,
such as reminders, brief status up dates, or gre etings.
Although these factors were less common overall, they provide d
valuable supp ort by improving the sp e e d and e˝ciency of routine
tasks. They help e d re duce the cognitive load on users, allowing
them to send contextually appropriate messages with minimal ef-
fort.
5.4 RQ4: Perceive d E˛e ctiveness of Di˛erent
LLMs
To implement LLM-assiste d text entr y in real-world environments,
it is essential to consider the mo del's p erformance, real-time re-
sp onsiveness, and deployability. We aim to explore how di˛erent
LLMs may in˚uence context-aware text suggestion tasks. Given
the inherently subje ctive nature of suggestion quality evaluation,
we conducte d a p ost-study assessment where participants evalu-
ate d the e˛e ctiveness of various LLMs base d on the data colle cte d
during the in-the-wild study. Participants' re call of the context was
grounde d in the re corde d textual data. Although this evaluation
cannot fully replicate the real-world setting of the study, we b elieve
that this preliminar y comparison still provides valuable empirical
insights.
5.4.1 Setup.
We primarily considere d Op enAI's GPT series [
42
],
Zhipu AI's GLM-4-9B [
22
], and Alibaba Cloud's Q wen2-7B-Instruct
[
64
]. These mo dels were chosen due to their strong p erformance
in Chinese language understanding, as demonstrate d in the 2024
August b enchmark rep ort from Sup er CLUE
3
[
63
]. We use d the
platform-provide d APIs for all tests and evaluate d mo dels smaller
or faster than GPT-4 Turb o (
gpt-4-1106-preview
), which was
use d in the in-the-wild study in Se ction 4. We also utilize d the
provide d interface to ˙ne-tune the mo dels with full parameters and
deploye d private instances when ne cessar y. LLM calls were made
using the same prompt and parameters as in CATIA.
Dataset
. Given that the minimum context window for the teste d
LLMs is 8k tokens and that some mo dels have input prompt length
limitations, we sele cte d 591 data samples from the study that met
these criteria. We split these samples into training and testing
sets in a 9:1 ratio, ensuring that the prop ortions of interp ersonal
communication and ser vice-oriente d tasks remaine d unchange d.
As a result, the training set containe d 533 samples, and the test set
containe d 58 samples. The training set was use d for ˙ne-tuning,
with the user-con˙rme d ˙nal text ser ving as the sole ground truth
in the output.
Pro ce dure
. After obtaining the pre dictions of di˛erent LLMs on
the test set, we invite d the original participants of each re cord to
re call the context at the time and, base d on consistent criteria, either
sele ct the b est option for each anonymize d mo del or cho ose none if
unsatis˙e d. The contextual information was converte d from JSON
into a human-readable natural language list. Participants were also
able to view the ˙nal text they con˙rme d during the in-the-wild
study for b etter re call of the context.
Metrics
. We considere d top-1 acceptance, top-4 acceptance, ˙rst
character time of the ˙rst suggestion, total resp onse time, API cost,
and ˙ne-tuning cost. Top-1 acceptance was chosen b e cause we
re queste d the mo dels to output suggestions in order of likeliho o d
(consistent with CATIA), re˚e cting pre cision. Additionally, the ˙ne-
tune d mo dels pro duce d only a single suggestion. Top-4 acceptance
was use d b e cause we re queste d a maximum of four suggestions,
which covers the full set of p ossible options and is in line with the
settings of in-the-wild study.
5.4.2 Results.
Table 6 presents the p erformance of a range of dif-
ferent LLMs, highlighting the b est-p erforming mo dels and their
resp e ctive metric results. The newer mo del
gpt-4o-2024-08-06
outp erforms
gpt-4-1106-preview
in terms of the acceptance rates,
demonstrating an evolve d p erformance of available LLMs. While
smaller base mo dels show slightly re duce d p erformance, their re-
sults are still comp etitive. Furthermore, while the ˙ne-tune d mo dels
do not show a signi˙cant improvement in top-4 acceptance rate
compare d to the base mo dels, they exhibit a notable increase in
top-1 acceptance rate. This suggests that when the task limits the
numb er of output options, ˙ne-tuning has the p otential to improve
the pre cision of the suggestions.
As for cost, all mo dels are cheap er than
gpt-4-1106-preview
.
In terms of sp e e d, all mo dels, except for
glm-4-9b
and its ˙ne-
tune d version, p erform faster. These results indicate that these
mo dels have the p otential to signi˙cantly enhance user interaction
resp onsiveness at a much lower cost.
3
Sup er CLUE: https://sup erclueai.com/
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
Table 5: Use cases and corresp onding examples of di˛erent o˛-scre en contextual factors that in˚uence text suggestions.
Context Factors Use Cases Examples
Lo cation (158)
The user clearly expresses their current lo cation or the
place they are going to.

I am eating in the cafeteria.

The information the user wants to convey can b e inferre d
from the lo cation.

It's really noisy, I just want to escap e!

Date and time (99)
The user's expression is relate d to the season or date.

Let's wait until the weather warms up to me et. No ne e d to brave this cold!

The user clearly expresses an approximate time. 
It's already late, I ne e d to sle ep. Let's talk tomorrow.

Calendar (45) The user mentione d plans for a sp e ci˙c time.

I'm fre e tomorrow afterno on during the ˙rst p erio d, and I'm available in
the evening as well.

The user's mo o d can b e inferre d from the sche dule. 
This we ek's sche dule is packe d, and I fe el like I can barely ke ep up.

Day of we ek (10) The user clearly mentione d a sp e ci˙c day of the we ek. 
It's Monday! You can start testing the AI and se e if it can pass, haha.

Activity (7) The user clearly mentione d an activity. 
I'm already biking on the road, I'll b e there so on.

The user's expression can b e inferre d from the activity. 
Wait for me two minutes, I'll b e there so on.

Table 6: Performance and cost comparison of various LLMs with or without ˙ne-tuning. Mo dels with (ft.) indicate ˙ne-tune d
versions. Bold highlights the top 3 b est-p erforming mo dels in each acceptance rate metric. Asterisks (*) indicate that the top-4
acceptance rate for ˙ne-tune d mo dels matches the corresp onding top-1 acceptance rate from the left. The values for the time
metrics represent the mean, with standard deviations in parentheses. API cost is shown as the average cost p er 1,000 calls.
Mo del
Top-1 Acpt.
Rate (%)
Top-4 Acpt.
Rate (%)
First Character
Time (s)
Total Resp onse
Time (s)
API Cost
($ / 1k calls)
Fine-tuning
Cost ($)
gpt-4-1106-preview 37.93
60.34
2.99 (0.46) 7.17 (1.80) 38.97 /
gpt-4o-2024-08-06 43.10
67.24
2.21 (0.55) 3.20 (0.80) 7.92 /
gpt-4o-mini-2024-07-18 18.97 55.17 2.67 (2.74) 3.70 (3.27) 0.48 /
glm-4-9b 20.69 53.45 4.22 (0.98) 7.68 (1.71) 0.80 /
qwen2-7b-instruct 25.86 53.45 2.99 (0.73) 4.02 (0.88) 0.42 /
gpt-4o-2024-08-06 (ft.)
56.89 56.89
* 2.54 (0.49) 2.68 (0.54) 11.26 113.19
gpt-4o-mini-2024-07-18 (ft.)
48.28
48.28 * 2.83 (1.02) 3.02 (1.04) 0.91 13.58
glm-4-9b (ft.) 44.82 44.82 * 3.30 (0.39) 3.64 (0.63) / 22.93
qwen2-7b-instruct (ft.)
50.00
50.00 * 2.62 (0.55) 2.75 (0.55) / 2.91
6 Discussion
Base d on the results of our user study, we identify two distinct
mental mo 
facilitator
and
collab orator
 re˚e ct how users
p erceive and interact with AI-driven text entr y systems. These mo d-
els inform several design dimensions and choices. In this se ction,
we ˙rst intro duce these mo dels and relate them to prior work, then
explore the design space for context-aware collab orative text entr y
on smartphones, and conclude with a discussion of p otential ethical
issues.
6.1
Mental Mo dels: Facilitator and Collab orator
Our ˙ndings suggest that users adopt two di˛erent mental mo dels
when interacting with collab orative text entr y systems (Figure 5).
These mo dels inform how AI systems should b e designe d to me et
var ying user ne e ds base d on the task at hand.
Facilitator
. In this mo del, the AI ser ves as a supp ortive to ol that
automates tasks or re duces user e˛ort, without re quiring signi˙-
cant manual input. This mo del is commonly applie d in task-driven
contexts, such as ˙lling out forms, entering structure d data, or p er-
forming searches. Our study also reveale d that the Facilitator mo del
Figure 5: Me ntal mo dels of human-AI collab orative text en-
tr y.
extends to certain so cial scenarios, particularly in cases where users
face situational impairments (e.g., walking, biking) or when sp e e d is
prioritize d over collab oration. For example, in situations re quiring
quick replies, users do not exp e ct to engage in a creative pro cess
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
with the AI; instead, they value pre cise, contextually aware sugges-
tions that can b e easily sele cte d with minimal e˛ort.
Collab orator
. In contrast, the Collab orator mo del represents a
more dynamic interaction where the AI acts as an e qual partner,
o˛ering diverse suggestions to inspire or assist the user in cre-
ative or explorator y tasks. This mo del is more relevant in scenarios
where the user's intent is
˚exible or evolving
, such as drafting so cial
messages, brainstorming ideas, or comp osing longer texts. In these
contexts, users exp e ct the AI to generate multiple options, allow-
ing them to explore di˛erent p ossibilities. Unlike the Facilitator
mo del, which fo cuses on pre cision, the Collab orator mo del thrives
on diversity and creativity. Users may trigger the AI to generate
suggestions even when they do
not
have a fully forme d intent, using
the AI as a source of inspiration to guide the collab oration. The
fo cus here is on o˛ering varie d options, allowing the user to sele ct,
re˙ne, or adapt the AI's suggestions as ne e de d.
These distinct mo dels emerge not only due to the di˛erent na-
ture of tasks but also b e cause the advance d capabilities of LLMs
have made users more aware of AI's p otential to inter vene in a
wider range of complex scenarios. Despite our study employe d the
same system and explicitly prompte d users with this is the same
assistant, users in op en-ende d, daily environments instinctively
adjuste d their exp e ctations base d on the dire ction and function
of the text. Furthermore, these mo dels in˚uence b oth how users
interact with AI and the cognitive e˛ort re quire d in these interac-
tions. Dep ending on the mo del adopte d, users exp erience di˛erent
levels of metacognitive demand. For instance, users in the Facili-
tator mo del generally engage in lighter metacognitive activities,
while the Collab orator mo del re quires more active involvement
in re˙ning and monitoring suggestions, guiding the AI through
iterative pro cesses. This aligns with previous work emphasizing
the metacognitive demands p ose d by generative AI [50, 53].
Beyond individual text comp osition, CATIA was pre dominantly
use d for interp ersonal communication, accounting for 90.42% of
cases in our study. This underscores the growing role of AI in help-
ing users articulate their thoughts and re˙ne their messages in
so cial interactions. Consistent with Fu et al.'s ˙ndings [
20
], users in
the Collab orator mo del expresse d a desire to leverage AI to b etter
articulate their thoughts and pro duce text b eyond their usual capa-
bilities. Users also note d that AI excels in formal communication.
Additionally, our Facilitator mo del aligns with Fu et al.'s suggestion
that AI can ser ve as a 
communication exp e diter,
 providing rapid
replies base d on the communication context.
While these ˙ndings align with Fu et al.'s work, we also iden-
ti˙e d notable di˛erences. In contrast to their obser vation that AI
use may b e unne cessar y and undesire d in informal, low-stakes
contexts (e.g., casual chats), users in our study, despite expressing
concerns ab out inauthentic replies, still relie d heavily on the sys-
tem in these contexts. We hyp othesize thre e p ossible reasons for
this. First, given the ubiquitous nature and inherent constraints
of text input on smartphones, the system's b for
quick replies or generating initial em to outweigh con-
cerns ab out authenticity. Se cond, unlike Fu et al.'s study, where
participants had to manually provide context for the AI, our system
eliminate d this re quirement. This re duce d barrier likely increase d
users' willingness to adopt the system. Finally, the sample size and
duration of our study may have intro duce d p otential biases. These
factors warrant further empirical investigation.
6.2 Design Space for Context-Aware
Collab orative Text Entr y
Building on our exploration of the mental mo dels, we prop ose the
following key dimensions and design considerations.
6.2.1 Contextual Cues for Mo del Di˙erentiation.
The distinction
b etwe en the Facilitator and Collab orator mo dels can b e e˛e ctively
inferre d from various elements on the smartphone, such as text ˙eld
hint text and interface structure [
5
,
37
], making this di˛erentiation
feasible in practical system design. Structure d ˙elds like search
bars, form inputs, or notes typically suggest the ne e d for Facilitator-
likee cise, concise, and dire ctly emb e dde d into the
work˚ow. In contrast, text ˙elds like message comp osition areas are
b etter suite d to the Collab orator mo del, where the AI can provide
a wider array of creative or explorator y options.
More over, the system can leverage o˛-scre en contextual factors,
such as the user's lo cation, activity, or time of day, alongside on-
scre en interface cues. For instance, if the user is on the move or
in a me eting ro om with p otential situational impairments, the sys-
tem might prioritize quick, task-oriente d suggestions (Facilitator).
Conversely, in less constraint scenarios, such as when the user is
engage d in a creative or brainstorming activity, the system could
incorp orate the user's physical context to o˛er suggestions that
broaden their thoughts (Collab or ator). Di˛erent users may prefer
di˛erent ways of leveraging contextual cues to inform their mental
mo del, which suggests that the system should continuously learn
from user interactions and adapt over time to b etter align with
individual preferences.
6.2.2 Initiation: System vs. User.
A critical design dimension is who
initiates the system or the user. In CATIA's current
implementation, suggestions are manually triggere d by the user.
However, base d on our inter views, whether an additional trigger
step is ne cessar y may var y dep ending on the scenario.
System-Initiate d Suggestions
(Facilitator). In task-oriente d
activities, users exp e ct the AI to provide automatic and unobtrusive
suggestions. These suggestions should b e contextually relevant,
minimizing the ne e d for manual inter vention. Unlike existing input
metho ds and app-base d suggestions, which are often binar y (on
or o˛ ), users in these situations prefer the system to o˛er re com-
mendations only when there is high con˙dence in their accuracy.
Any incorre ct or irrelevant suggestions risk breaking user trust,
esp e cially in contexts where sp e e d and pre cision are crucial (e.g.,
quickly ˙lling a form or exe cuting a search). To minimize distrac-
tions, the system can pre dict user intent to input text base d on past
digital traces and only o˛er automatic suggestions in user-sp e ci˙e d
text ˙elds. Additionally, non-intrusive icons can indicate available
suggestions, allowing users to expand them as ne e de d.
User-Initiate d Suggestions
(Collab orator). In op en-ende d tasks,
where users se ek creativity, exploration, or multiple options, they
are more incline d to actively engage the AI to generate suggestions.
In such cases, users may ˙rst want to obser ve how the AI resp onds
b efore shaping their own intentions. The system, however, cannot
anticipate whether the user has a fully forme d idea or is lo oking
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
for inspiration, and premature autonomous AI suggestions may
disrupt the user's ˚ow or prematurely ste er the dire ction of the
task. By allowing users to initiate suggestions themselves, a calmer
design approach can help them retain control over the interac-
tion, ensuring that the AI's contributions align with their creative
pro cess.
6.2.3 Suggestion: Pre cision vs. Diversity.
Dep ending on the context,
the system should either prioritize pre cision or embrace diversity.
Pre cision-Oriente d Suggestions
(Facilitator). Users ne e d
fewer
but more accurate
suggestions in e˝ciency-driven tasks. Given
that users have limite d cognitive resources to evaluate numerous
options, it is essential for the AI to ˙lter out the most relevant
contextual information to provide pre cise pre dictions. For example,
in our study, certain apps o˛ere d multiple in-app search suggestions,
but CATIA was able to re˙ne these suggestions by leveraging cross-
app histor y to present more accurate search terms. Se ction 5.4 also
suggests that such an approach is feasible by showing ˙ne-tune d
mo dels can improve accuracy with fewer options.
Diversity-Oriente d Suggestions
(Collab orator). In explorator y
or creative tasks, users se ek
diverse and informative
suggestions.
Pre cision is less critical in these contexts; instead, users value having
access to a broader range of p ossibilities. Our study found that
by leveraging
additional contextual data
, such as lo cation or past
interactions, the system was able to generate options that, while
unexp e cte d, were still acceptable and useful to users. This ability
to present a diverse array of suggestions enhances collab oration
by o˛ering new p ossibilities, thereby supp orting users in re˙ning
their ideas and engaging in more meaningful creative exploration.
6.2.4 Interface: Single-Action vs. Conversational.
The design of the
interface is key to supp orting di˛erent mo des of interaction. While
CATIA is currently designe d as a p opup panel, var ying tasks may
demand di˛erent interface approaches.
Single-Action Interface
(Facilitator). In the Facilitator mo del,
users exp e ct the AI to integrate seamlessly with existing input meth-
o ds, such as the smartphone keyb oard, and to apply suggestions
with a single tap. They prefer minimal disruption to their work˚ow,
with no additional learning or interaction costs. In this case, the
single-action interface is ideal, as it emb e ds suggestions dire ctly
into the user's input ˙eld, allowing for quick, e˛ortless integration
of AI-generate d text.
Conversational Interface
(Collab orator). Users in the Collab o-
rator mo del may b ene˙t from a more interactive, conversational-
style interface that fosters de ep er engagement with the AI. In this
setup, a de dicate d panel or dialogue interface enables a back-and-
forth exchange, where users can iteratively re˙ne and regenerate
suggestions. Unlike purely text-base d interactions,
the reasoning
b ehind suggestions or the AI's thought pro cess
can b e optionally
displaye d, as suggeste d by [
53
] and [
50
], helping to inspire users'
intent b eyond just the literal text. More over, users sometimes have a
clear intent to express but may b e constraine d by mobile conditions.
In such cases, the AI can proactively highlight and inquire ab out the
missing parts of the user's intende d message, as prop ose d by [
67
].
This share d interaction space facilitates a richer, more meaningful
collab oration [
48
], as users gain insights into the AI's underlying
logic, thus encouraging creative exploration and providing more
control over the ˙nal output.
6.3 Ethical Issues
6.3.1 O ver-reliance and Cognitive Manipulation.
Although context-
aware text suggestions can b ene˙t users, they may also shift situa-
tional reasoning to the AI, p otentially in˚uencing human thoughts
and de cisions [
53
]. More over, AI suggestions may inherit biases
from their training data [
40
,
60
]. If the system prioritizes content
from sp e ci˙c sources or favors certain typ es of queries, it could in-
advertently reinforce existing biases or limit information diversity.
For example, search suggestions base d on a user's past purchases
or interests might narrow their p ersp e ctive or unintentionally fa-
vor sp e ci˙c companies or pro ducts. Another critical issue is the
subtle integration of advertisements or promotional content into
AI-generate d suggestions. While users may trust the system, prior-
itizing sp onsore d content or subtly nudging users toward certain
choices risks manipulating their de cisions without their full aware-
ness.
To mitigate these risks, te chnology providers should work to
re duce inherent biases in LLMs and intro duce external oversight
me chanisms. At the same time, active user involvement is e qually
imp ortant. Systems should o˛er clear explanations for each sugges-
tion, outlining its source and rationale. In high-stakes situations,
such as ˙nancial de cisions, or user-designate d critical contexts,
users should b e re quire d to pause and review these explanations.
This aligns with the seamful design approach prop ose d in prior
work [18, 27, 53].
6.3.2 Data Privacy and User Control.
Colle cting user context infor-
mation comes at the cost of privacy, and participants in our study
expresse d corresp onding concerns. In our study, we use d a de di-
cate d LLM API, with all data se curely store d on a de dicate d ser ver.
This approach ensure d that sensitive information was prote cte d.
However, we acknowle dge that in practical deployments, stronger
privacy measures will b e ne cessar y.
A key avenue for addressing these concerns is through on-device
LLM deployment, w hich would enable the pro cessing of user data
lo cally, without ne e ding to upload sensitive contextual informa-
tion to the cloud. Our ˙ndings (Se ction 5.4) indicate that smaller
mo dels have the p otential to p erform well in such scenarios, thus
supp orting the feasibility of this dire ction.
Another critical privacy issue is scre en content capture. Our
˙ndings (Se ction 5.1.2) suggest that the information on a single
scre en is often su˝cient for providing text suggestions. As a result,
future systems could re duce the ne e d for continuous scre en content
colle ction.
Additionally, providing users with granular control over what
data is colle cte d can emp ower them to make informe d de cisions
ab out their privacy. Optional data colle ction p olicies could include
re quirements on sp e ci˙c sensors or typ es of data, as well as the
duration of data colle ction. Users could also sp e cify more re˙ne d
control rules for data colle ction.
7 Limitations and Future Work
7.1 Data Colle ction and Contextual Factors
Our study colle cte d one we ek of in-the-wild data from Chinese stu-
dent participants. While the data volume and participant diversity
are limite d, we do not claim that our study encompasses all p ossible
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
use cases or is generalizable to all p opulations. Nevertheless, we
b elieve that this case study provides valuable insights for other
research exploring Human-AI collab oration and the application of
LLMs in ever yday contexts for end users.
The contextual variables we colle cte d were diverse but restricte d
to textual data, leaving ro om for future expansion and improvement.
For instance, b eyond capturing scre en content through accessibility
APIs, future studies could integrate image or vide o data, which
would b e particularly relevant for richer so cial me dia platforms.
7.2 Choice of LLMs and Prompting Metho ds
This study employe d GPT-4 Turb o, which, while e˛e ctive, is rel-
atively costly and slower compare d to some newer mo dels (se e
Se ction 5.4). Due to the time constraints of the study, we chose
this mo del, though it may not b e the optimal choice for real-world
deployment. Future implementations should consider more light-
weight, faster, and cost-e˛e ctive LLMs that are b etter suite d for
seamless integration into ever yday use.
Additionally, we fe d contextual information into the prompt in
JSON format, which may not ne cessarily b e the most optimal struc-
ture [
52
]. The scre en content was input as a list of text fragments
without sp e ci˙c organization. There may b e more e˛e ctive ways to
represent this data, which could enhance the LLM's ability to rea-
son ab out the contextual information and generate more accurate
suggestions.
7.3 Future Dire ctions
We are fo cusing on re˙ning the system to b etter align with the
prop ose d mental mo dels. Future studies could involve more diverse
participant groups with varie d backgrounds, including di˛erent
cultures, o ccupations, and age ranges, to enhance the generaliz-
ability of our ˙ndings. An interesting dire ction is exploring how
non-native sp eakers use similar systems on mobile devices, where
the ne e d for e˛e ctive human-AI collab oration may b e even greater.
This could o˛er insights into overcoming language barriers in di-
verse real-world settings. Additionally, long-term studies will b e
ne cessar y to obser ve how users adapt to AI collab oration over
time and how AI systems can evolve their suggestions base d on
sustaine d interaction.
8 Conclusion
This pap er investigates human-AI collab orative text entr y on smart-
phones using large language mo dels (LLMs). We develop e d a context-
aware text input assistant (CATIA), which provides text suggestions
base d on contextual factors such as scre en content, time, lo cation,
and user activity.
In a 7-day in-the-wild study with 36 participants, we found that
the system provide d appropriate suggestions in over 80% of cases,
primarily in two key scenarios: interp ersonal communication and
information ser vices. The collab oration b etwe en users and the
system demonstrate d its e˛e ctiveness in re ducing cognitive load.
While the system mainly relie d on scre en-base d information to
infer input text, o˛-scre en factors also prove d useful in sp e ci˙c
contexts. Additionally, an o˜ine evaluation of various LLMs on
the colle cte d dataset showe d that smaller, faster, and more cost-
e˛e ctive mo dels could p otentially achieve results comparable to
the larger mo del use d in our study, making them more practical for
real-world applications.
We identi˙e d two distinct mental mo dels of human-AI collab ora-
tive text entr y: in e˝ciency-driven tasks, the AI is exp e cte d to act
as a supp ortive facilitator, while in more complex, creative tasks, it
is viewe d as an e qual collab orator. We also outline d design options
across di˛erent dimensions to supp ort these mo dels.
Our work provides empirical evidence for human-AI collab ora-
tive text entr y and o˛ers insights into the design and implementa-
tion of LLM-base d systems for real-world end-user applications.
Acknowle dgments
This work is supp orte d by the National Key Research and Devel-
opment P lan of China under Grant No. 2024YFB4505500 & No.
2024YFB4505502, Beijing Key Lab of Networke d Multime dia, Insti-
tute for Arti˙cial Intelligence, Tsinghua University (TH UAI), Beijing
National Research Center for Information Science and Te chnology
(BNRist), 2025 Key Te chnological Innovation Program of Ningb o
City under Grant No. 2022Z080, Beijing Municipal Science and Te ch-
nology Commission, Administrative Commission of Zhongguancun
Science Park No. Z221100006722018, and Science and Te chnology
Innovation Key R&D Program of Chongqing.

## References
[1]
Mohammad Aliannejadi, Hame d Zamani, Fabio Crestani, and W. Bruce Croft.
2021. Context-aware Target Apps Sele ction and Re commendation for Enhancing
Personal Mobile Assistants.
ACM Transactions on Information Systems
39, 3 (2021),
29:129:30. doi:10.1145/3447678
[2]
Sale ema Amershi, Dan Weld, Mihaela Vor voreanu, Adam Fourney, Besmira
Nushi, Penny Collisson, Jina Suh, Shamsi Iqbal, Paul N. Bennett, Kori Inkp en,
Jaime Te evan, Ruth Kikin-Gil, and Eric Hor vitz. 2019. Guidelines for Human-
AI Interaction. In
Pro ce e dings of the 2019 CHI Conference on Human Factors
in Computing Systems
. ACM, New York, N Y, USA, 113. do i:10.1145/3290605.
3300233
[3]
 Apple. 2024. Apple Intelligence. https://w w w.apple.com/apple- intelligence/
[4]
Timothy J. Aveni, Armando Fox, and Björn Hartmann. 2023. Bringing Context-
Aware Completion Suggestions to Arbitrar y Text Entr y Interfaces. In
Adjunct
Pro ce e dings of the 36th Annual ACM Symp os ium on User Interface Software and
Te chnology (UIST '23 Adjunct)
. Asso ciation for Computing Machiner y, New York,
N Y, USA, 13. doi:10.1145/3586182.3615825
[5]
F lorian Bemmann, Timo Ko ch, Maximilian Bergmann, Clemens Stachl, Daniel
Buschek, Ramona Scho e del, and Sven Mayer. 2024. Putting Language into Con-
text Using Smartphone-Base d Keyb oard Logging. doi:10.48550/arXiv.2403.05180
arXiv:2403.05180 [cs].
[6]
Priyanka Bhatele and Mangesh Be dekar. 2023. Sur vey on Smartphone Sensors
and User Intent in Smartphone Usage. In
2023 IEEE 8th International Conference
for Convergence in Te chnology (I2CT)
. 19. doi:10.1109/I2CT57861.2023.10126192
[7]
Xiaojun Bi and Shumin Zhai. 2016. IJQ werty: What Di˛erence Do es One Key
Change Make? Gesture Typing Keyb oard Optimization Bounde d by One Key
Position Change from Q werty. In
Pro ce e dings of the 2016 CHI Conference on Human
Factors in Computing Systems (CHI '16)
. Asso ciation for Computing Machiner y,
New York, N Y, USA, 4958. doi:10.1145/2858036.2858421
[8]
Rishi Bommasani, Drew A. Hudson, Ehsan Adeli, Russ Altman, Simran Arora,
Sydney von Ar x, Michael S. Bernstein, Jeannette Bohg, Antoine Bosselut, Emma
Brunskill, Erik Br ynjolfsson, Shyamal Buch, Dallas Card, Ro drigo Castellon,
Niladri Chatterji, Annie Chen, Kathle en Cre el, Jare d Quincy Davis, Dora Dem-
szky, Chris Donahue, Moussa Doumb ouya, Esin Durmus, Stefano Ermon, John
Etchemendy, Kawin Ethayarajh, Li Fei-Fei, Chelsea Finn, Trevor Ga le, Lauren
Gillespie, Karan Go el, Noah Go o dman, Shelby Grossman, Ne el Guha, Tatsunori
Hashimoto, Peter Henderson, John Hewitt, Daniel E. Ho, Jenny Hong, Kyle Hsu,
Jing Huang, Thomas Icard, Saahil Jain, Dan Jurafsky, Pratyusha Kalluri, Siddharth
Karamcheti, Ge o˛ Ke eling, Fereshte Khani, Omar Khattab, Pang Wei Koh, Mark
Krass, Ranjay Krishna, Rohith Kuditipudi, Ananya Kumar, Faisal Ladhak, Mina
Le e, Tony Le e, Jure Leskove c, Isab elle Levent, Xiang Lisa Li, Xue chen Li, Tengyu
Ma, Ali Malik, Christopher D. Manning, Suvir Mirchandani, Eric Mitchell, Zanele
Munyikwa, Suraj Nair, Avanika Narayan, De epak Narayanan, Ben Newman,
Allen Nie, Juan Carlos Niebles, Hame d Nilforoshan, Julian Nyarko, Giray Ogut,
Laurel Orr, Isab el Papadimitriou, Jo on Sung Park, Chris Pie ch, Eva Portelance,
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
Christopher Potts, Aditi Raghunathan, Rob Reich, Hongyu Ren, Frie da Rong,
Yusuf Ro ohani, Camilo Ruiz, Jack Ryan, Christopher Ré, Dorsa Sadigh, Shiori
Sagawa, Keshav Santhanam, Andy Shih, Krishnan Srinivasan, Alex Tamkin, Ro-
han Taori, Armin W. Thomas, F lorian Tramèr, Rose E. Wang, William Wang,
Bohan Wu, Jiajun Wu, Yuhuai Wu, Sang Michael Xie, Michih iro Yasunaga, Ji-
axuan You, Matei Zaharia, Michael Zhang, Tianyi Zhang, Xikun Zhang, Yuhui
Zhang, Lucia Zh en g, Kaitlyn Zhou, and Percy Liang. 2022. On the Opp ortunities
and Risks of Foundation Mo dels. doi:10.48550/arXiv.2108.07258 arXiv:2108.07258
[cs].
[9]
Tom Brown, Benjamin Mann, Nick Ryder, Melanie Subbiah, Jare d D Kaplan,
Prafulla Dhariwal, Ar vind Ne elakantan, Pranav Shyam, Girish Sastr y, Amanda
Askell, Sandhini Agar wal, Ariel Herb ert-Voss, Gretchen Krueger, Tom Henighan,
Rewon Child, Aditya Ramesh, Daniel Ziegler, Je˛rey Wu, Clemens Winter,
Chris Hesse, Mark Chen, Eric Sigler, Mateusz Litwin, Scott Gray, Benjamin
Chess, Jack Clark, Christopher Berner, Sam McCandlish, Ale c Radford, Ilya
Sutskever, and Dario Amo dei. 2020. Language Mo dels are Few-Shot Learn-
ers. In
Advances in Neural Information Pro cessing Systems
, Vol. 33. Curran
Asso ciates, Inc., 18771901. https://pro ce e dings.neurips.cc/pap er/2020/hash/
1457c0d6bfcb4967418bf b8ac142f64a- Abstract.html
[10]
Runze Cai, Nuwan Janaka, Yang Chen, Lucia Wang, Shengdong Zhao, and Can Liu.
2024. PANDALens: Towards AI-Assiste d In-Context Writing on OHMD During
Travels. In
Pro ce e dings of the CHI Conference on Human Factors in Computing
Systems (CHI '24)
. Asso ciation for Computing Machiner y, New York, N Y, USA,
124. doi:10.1145/3613904.3642320
[11]
Claudio Carpineto and Giovanni Romano. 2012. A Sur vey of Automatic Quer y
Expansion in Information Retrieval.
Comput. Sur veys
44, 1 (2012), 1:11:50.
doi:10.1145/2071389.2071390
[12]
Juan Pablo Carrascal and Karen Church. 2015. An In-Situ Study of Mobile App
& Mobile Search Interactions. In
Pro ce e dings of the 33rd Annual ACM Conference
on Human Factors in Computing Systems (CHI '15)
. Asso ciation for Computing
Machiner y, New York, N Y, USA, 27392748. doi:10.1145/2702123.2702486
[13]
**Weihao Chen**, Chun Yu, Huadong Wang, Zheng Wang, Lichen Yang, Yukun Wang,
**Weinan Shi**, and Yuanchun Shi. 2023. From Gap to Synergy: Enhancing Contextual
Understanding through Human-Machine Collab oration in Personalize d Systems.
In
Pro ce e dings of the 36th Annual ACM Symp osium on User Inter face Software
and Te chnology (UIST '23)
. Asso ciation for Computing Machiner y, New York, N Y,
USA, 115. doi:10.1145/3586183.3606741
[14]
Chenhui Cui, Tao Li, Junjie Wang, Chunyang Chen, Dave Towey, and Rubing
Huang. 2024. Large Language Mo dels for Mobile GUI Text Input Generation: An
Empirical Study. doi:10.48550/arXiv.2404.08948 arXiv:2404.08948 [cs].
[15]
Anind K. Dey. 2001. Understanding and Using Context.
Personal and Ubiquitous
Computing
5, 1 (Feb. 2001), 47. doi:10.1007/s007790170019 Publisher: Springer.
[16]
Trinh Minh Tri Do, Jan Blom, and Daniel Gatica-Perez. 2011. Smartphone usage
in the wild: a large-scale analysis of applications and context. In
Pro ce e dings of
the 13th international conference on multimo dal interfaces (ICMI '11)
. Asso ciation
for Computing Machiner y, New York, N Y, USA, 353360. doi:10.1145/2070481.
2070550
[17]
Qing xiu Dong, Lei Li, Damai Dai, Ce Zheng, Zhiyong Wu, Baobao Chang, Xu
Sun, Jingjing Xu, Lei Li, and Zhifang Sui. 2023. A Sur vey on In-context Learning.
doi:10.48550/arXiv.2301.00234 arXiv:2301.00234 [cs].
[18]
Up ol Ehsan, Q. Vera Liao, Samir Passi, Mark O. Rie dl, and Hal Daumé. 2024.
Seamful XAI: Op erationalizing Seamful Design in Explainable AI.
Pro c. ACM
Hum.-Comput. Interact.
8, CSCW1 (2024), 119:1119:29. doi:10.1145/3637396
[19]
Andrew Fowler, Kurt Partridge, Ciprian Chelba, Xiaojun Bi, Tom Ouyang, and
Shumin Zhai. 2015. E˛e cts of Language Mo deling and its Personalization on
Touchscre en Typing Performance. In
Pro ce e dings of the 33rd Annual ACM Confer-
ence on Hum an Factors in Computing Systems (CHI '15)
. Asso ciation for Computing
Machiner y, New York, N Y, USA, 649658. doi:10.1145/2702123.2702503
[20]
Yue Fu, Sami Fo ell, Xuhai Xu, and Alexis Hiniker. 2024. From Text to Self:
Users' Perception of AIMC To ols on Interp ersonal Communication and Self. In
Pro ce e dings of the 2024 CHI Conference on Human Factors in Computing Systems
(CHI '24)
. Asso ciation for Computing Machiner y, New York, N Y, USA, 117.
doi:10.1145/3613904.3641955
[21]
Nestor Garay-Vitoria and Julio Abascal. 2006. Text pre diction systems: a sur vey.
Universal Access in the Information So ciety
4, 3 (March 2006), 188203. doi:10.
1007/s10209- 005- 0005- 9
[22]
Team GLM, Aohan Zeng, Bin Xu, Bowen Wang, Chenhui Zhang, Da Yin, Dan
Zhang, Diego Rojas, Guanyu Feng, Hanlin Zhao, Hanyu Lai, Hao Yu, Hongning
Wang, Jiadai Sun, Jiajie Zhang, Jiale Cheng, Jiayi Gui, Jie Tang, Jing Zhang,
Jingyu Sun, Juanzi Li, Lei Zhao, Lindong Wu, Lucen Zhong, Mingdao Liu, Minlie
Huang, Peng Zhang, Qinkai Zheng, Rui Lu, Shuaiqi Duan, Shudan Zhang, Shulin
Cao, Shuxun Yang, Weng Lam Tam, Wenyi Zhao, Xiao Liu, Xiao X ia, Xiaohan
Zhang, Xiaotao Gu, Xin Lv, Xinghan Liu, Xinyi Liu, Xinyue Yang, Xixuan Song,
Xunkai Zhang, Yifan An, Yifan Xu, Yilin Niu, Yuantao Yang, Yueyan Li, Yushi Bai,
Yuxiao Dong, Zehan Qi, Zhaoyu Wang, Zhen Yang, Zheng xiao Du, Zhenyu Hou,
and Zihan Wang. 2024. ChatGLM: A Family of Large Language Mo dels from
GLM-130B to GLM-4 All To ols. doi:10.48550/arXiv.2406.12793 arXiv:2406.12793.
[23]
Mayank Go el, Alex Jansen, Travis Mandel, Shwetak N. Patel, and Jacob O. Wob-
bro ck. 2013. ContextTyp e: using hand p osture information to improve mobile
touch scre en text entr y. In
Pro ce e dings of the SIGCHI Conference on Human Factors
in Computing Systems (CHI '13)
. Asso ciation for Computing Machiner y, New
York, N Y, USA, 27952798. doi:10.1145/2470654.2481386
[24]
Joshua T. Go o dman. 2001. A bit of progress in language mo deling.
Computer
Sp e e ch & Language
15, 4 (Oct. 2001), 403434. doi:10.1006/csla.2001.0174
[25]
Wenlong Huang, Pieter Abb e el, De epak Pathak , and Igo r Mordatch. 2022. Lan-
guage Mo dels as Zero-Shot P lanners: Extracting Actionable Knowle dge for Em-
b o die d Agents. In
International Conference on Machine Learning
. PMLR, 9118
9147. https://pro ce e dings.mlr.press/v162/huang22a.html ISSN: 2640-3498.
[26]
Perttu Hämäläinen, Mikke Tavast, and Anton Kunnari. 2023. Evaluating Large
Language Mo dels in Generating Synthetic HCI Research Data: a Case Study. In
Pro ce e dings of the 2023 CHI Conference on Human Factors in Computing Systems
(CHI '23)
. Asso ciation for Computing Machiner y, New York, N Y, USA, 119.
doi:10.1145/3544548.3580688
[27]
Sarah Inman and David Rib es. 2019. "Beautiful Seams": Strategic Revelations and
Concealments. In
Pro ce e dings of the 2019 CHI Conference on Human Factors in
Computing Systems (CHI '19)
. Asso ciation for Computing Machiner y, New York,
N Y, USA, 114. doi:10.1145/3290605.3300508
[28]
Mar yam Kamvar and Shume et Baluja. 2007. The role of context in quer y input:
using contextual signals to complete queries on mobile devices. In
Pro ce e dings
of the 9th international conference on Human computer interaction with mobile
devices and ser vices (MobileHCI '07)
. Asso ciation for Computing Machiner y, New
York, N Y, USA, 405412. doi:10.1145/1377999.1378046
[29]
Jare d Kaplan, Sam McCandlish, Tom Henighan, Tom B. Brown, Benjamin
Chess, Rewon Child, Scott Gray, Ale c Radford, Je˛rey Wu, and Dario Amo dei.
2020. Scaling Laws for Neural Language Mo dels. doi:10.48550/arXiv.2001.08361
arXiv:2001.08361 [cs].
[30]
Per Ola Kristensson. 2009. Five Challenges for Intelligent Text Entr y Metho ds.
AI Magazine
30, 4 (Sept. 2009), 8585. doi:10.1609/aimag.v30i4.2269 Numb er: 4.
[31]
Per Ola Kristensson, Stephen Brewster, James Clawson, Mark Dunlop, Leah
Findlater, Poika Isokoski, Benoît Martin, Antti Oulasvirta, Keith Vertanen, and
Annalu Waller. 2013. Grand challenges in text entr y. In
CHI '13 Extende d Abstracts
on Human Factors in Computing Systems (CHI EA '13)
. Asso ciation for Computing
Machiner y, New York, N Y, USA, 33153318. doi:10.1145/2468356.2479675
[32]
Vladimir I. Levenshtein . 1966. Binar y co des capable of corre cting deletions,
insertions, and reversals.
Soviet P hysics-Doklady
10 (1966), 707710. https:
//api.semanticscholar.org/CorpusID:60827152
[33]
Toby Jia-Jun Li and Brad A. Myers. 2021. A Ne e d-˙nding Study for Under-
standing Text Entr y in Smartphone App Usage. doi:10.48550/arXiv.2105.10127
arXiv:2105.10127 [cs].
[34]
Q. Vera Liao, Daniel Gruen, and Sarah Miller. 2020. Questioning the AI: Informing
Design Practices for Explainable AI User Exp eriences. In
Pro ce e dings of the 2020
CHI Conference on Human Factors in Computing Systems (CHI '20)
. Asso ciation for
Computing Machiner y, New York, N Y, USA, 115. doi:10.1145/3313831.3376590
[35]
Q. Vera Liao and Jennifer Wortman Vaughan. 2 023. AI Transparency in the Age
of LLMs: A Human-Centere d Research Roadmap. doi:10.48550/arXiv.2306.01941
arXiv:2306.01941.
[36]
Zhe Liu, Chunyang Chen, Junjie Wang, Xing Che, Yuekai Huang, Jun Hu, and
Qing Wang. 2023. Fill in the Blank: Context-Aware Automate d Text Input Gener-
ation for Mobile GUI Testing. In
Pro ce e dings of the 45th International Conference
on Software Engine ering (ICSE '23)
. IEEE Press, Melb ourne, Victoria, Australia,
13551367. doi:10.1109/ICSE48619.2023.00119
[37]
Zhe Liu, Chunyang Chen, Junjie Wang, Mengzhuo Chen, Boyu Wu, Yuekai
Huang, Jun Hu, and Qing Wang. 2024. Unblind Text Inputs: Pre dicting Hint-text
of Text Input in Mobile Apps via LLM. In
Pro ce e dings of the 2024 CHI Conference
on Human Factors in Computing Systems (CHI '24)
. Asso ciation for Computing
Machiner y, New York, N Y, USA, 120. doi:10.1145/3613904.3642939
[38]
I. Scott MacKenzie and R. William So ukore˛. 2002. Text En-
tr y for Mobile Computing: Mo dels and Metho ds,The or y and Prac-
tice.
HumanComputer Interaction
17, 2-3 (Sept. 2002), 147198.
doi:10.1080/07370024.2002.9667313 Publisher: Taylor & Francis _eprint:
https://w w w.tandfonline.com/doi/p df/10.1080/07370024.2002.9667313.
[39]
Grégoire Mialon, Rob erto Dessì, Maria Lomeli, Christoforos Nalmpantis, Ram
Pasunuru, Rob erta Raileanu, Baptiste Rozière, Timo Schick, Jane D wive di-Yu, Asli
Celikyilmaz, Edouard Grave, Yann LeCun, and Thomas Scialom. 2023. Augmente d
Language Mo dels: a Sur vey. (Feb. 2023). http://ar xiv.org/abs/2302.07842 arXiv:
2302.07842.
[40]
Moin Nade em, Anna Bethke, and Siva Re ddy. 2021. Stere oSet: Measuring stere o-
typical bias in pretraine d language mo dels. In
Pro ce e dings of the 59th Annual
Me eting of the Asso ciation for Computational Linguistics and the 11th Interna-
tional Joint Conference on Natural Language Pro cessing (Volume 1: Long Pap ers)
,
Chengqing Zong, Fei Xia, Wenjie Li, and Rob erto Navigli (Eds.). Asso ciation for
Computational Linguistics, Online, 53565371. doi:10.18653/v1/2021.acl- long.416
[41]
 Op enAI. 2022. Intro ducing ChatGPT. https://op enai.com/blog /chatgpt
[42]
 Op enAI. 2024. Op enAI Mo dels. https://platform.op enai.com/do cs/m o dels
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
[43]
Antti Oulasvirta, Anna Reichel, Wenbin Li, Yan Zhang, Myroslav Bachynskyi,
Keith Vertanen, and Per Ola Kristensson. 2013. Improving two-thumb text entr y
on touchscre en devices. In
Pro ce e dings of the SIGCHI Conference on Human Factors
in Computing Systems (CHI '13)
. Asso ciation for Computing Machiner y, New
York, N Y, USA, 27652774. doi:10.1145/2470654.2481383
[44]
Antti Oulasvirta, Sakari Tamminen, Virpi Roto, and Jaana Kuorelahti. 2005. In-
teraction in 4-se cond bursts: the fragmente d nature of attentional resources in
mobile HCI. In
Pro ce e dings of the SIGCHI Conference on Human Factors in Com-
puting Systems (CHI '05)
. Asso ciation for Computing Machiner y, New York, N Y,
USA, 919928. doi:10.1145/1054972.1055101
[45]
Martin Pielot, Karen Church, and Ro drigo de Oliveira. 2014. An in-situ study
of mobile phone noti˙cations. In
Pro ce e dings of the 16th international conference
on Human-computer interaction with mobile devices & ser vices (MobileHCI '14)
.
Asso ciation for Computing Machiner y, New York, N Y, USA, 233242. doi:10.
1145/2628363.2628364
[46]
Changle Qu, Sunhao Dai, Xiao chi Wei, Hengyi Cai, Shuaiqiang Wang, Dawei
Yin, Jun Xu, and Ji-rong Wen. 2025. To ol learning with large language mo dels: a
sur vey.
Frontiers of Computer Science
19, 8 (Jan. 2025), 198343. doi:10.1007/s11704-
024- 40678- 2
[47]
P hilip Quinn and Shumin Zhai. 2016. A Cost-Bene˙t Study of Text Entr y Sugges-
tion Interaction. In
Pro ce e dings of the 2016 CHI Conference on Human Factors in
Computing Systems (CHI '16)
. Asso ciation for Computing Machiner y, New York,
N Y, USA, 8388. doi:10.1145/2858036.2858305
[48]
Jeba Rezwana and Mar y Lou Maher. 2023. Designing Creative AI Partners with
COFI: A Framework for Mo deling Interaction in Human-AI Co-Creative Systems.
ACM Trans. Comput.-Hum. Interact.
30, 5 (2023), 67:167:28. doi:10.1145/3519026
[49]
Tapio Soikkeli, Juuso Karikoski, and Heikki Hammainen. 2011. Diversity and
End User Context in Smartphone Usage Sessions. In
2011 Fifth International
Conference on Next Generation Mobile Applications, Ser vices and Te chnologies
.
712. doi:10.1109/NGMAST.2011.12 ISSN: 2161-2897.
[50]
Hari Subramonyam, Roy Pea, Christopher Pondo c, Mane esh Agrawala, and
Colle en Seifert. 2024. Bridging the Gulf of Envisioning: Cognitive Challenges in
Prompt Base d Interactions with LLMs. In
Pro ce e dings of the 2024 CHI Conference
on Human Factors in Computing Systems (CHI '24)
. Asso ciation for Computing
Machiner y, New York, N Y, USA, 119. doi:10.1145/3613904.3642754
[51]
The o dore R. Sumers, Shunyu Yao, Karthik Narasimhan, and Thomas L. Gri˝ths.
2023. Cognitive Archite ctures for Language Agents. doi:10.48550/arXiv.2309.
02427 arXiv:2309.02427 [cs].
[52]
Zhi Rui Tam, Cheng-Kuang Wu, Yi-Lin Tsai, Chieh-Yen Lin, Hung-yi Le e, and
Yun-Nung Chen. 2024. Let Me Sp eak Fre ely? A Study on the Impact of Format
Restrictions on Performance of Large Language Mo dels. doi:10.48550/arXiv.2408.
02442 arXiv:2408.02442 [cs].
[53]
Lev Tankelevitch, Viktor Kewenig, Auste Simkute, Ava Elizab eth Scott, Advait
Sarkar, Abigail Sellen, and Sean Rintel. 2024. The Metacognitive Demands and
Opp ortunities of Generative AI. In
Pro ce e dings of the 2024 CHI Conference on
Human Factors in Computing Systems (CHI '24)
. Asso ciation for Computing
Machiner y, New York, N Y, USA, 124. doi:10.1145/3613904.3642902
[54]
Jaime Te evan, Amy Karlson, Shah riyar Amini, A. J. Bernheim Brush, and John
Krumm. 2011. Understanding the imp ortance of lo cation, time, and p e ople in
mobile lo cal search b ehavior. In
Pro ce e dings of the 13th International Conference
on Human Computer Interaction with Mobile Devices and Ser vices (MobileHCI '11)
.
Asso ciation for Computing Machiner y, New Yo rk, N Y, USA, 7780. doi:10.1145/
2037373.2037386
[55]
Michael Terr y, Chinmay Kulkarni, Martin Wattenb erg, Lucas Dixon, and Mere d-
ith Ringel Morris. 2024. Interactive AI Alignment: Sp e ci˙cation, Pro cess, and
Evaluation Alignment. doi:10.48550/arXiv.2311.00710 arXiv:2311.00710.
[56]
Shiu Lun Tsang and Siobhan Clarke. 2007. Mining User Mo dels for E˛e ctive
Adaptation of Context-Aware Applications. In
The 2007 International Conference
on Intelligent Per vasive Computing (IPC 2007)
. 178187. doi:10.1109 /IPC.2007.108
[57]
Ashish Vaswani, Noam Shaze er, Niki Parmar, Jakob Uszkoreit, Llion Jones,
Aidan N Gomez, −ukasz Kaiser, and Illia Polosukhin. 2017. Attention is
All you Ne e d. In
Advances in Neural Information Pro cessing Systems
, Vol. 30.
Curran Asso ciates, Inc. https://pro ce e dings.neurips.cc/pap er/2017/hash/
3f5e e243547de e91f b d053c1c4a845aa- Abstract.html
[58]
Keith Vertanen, Mark Dunlop, James Clawson, Per Ola Kristensson, and
Ahme d Sabbir Arif. 2016. Inviscid Text Entr y and Beyond. In
Pro ce e dings of
the 2016 CHI Conference Extende d Abstracts on Human Factors in Computing Sys-
tems (CHI EA '16)
. Asso ciation for Computing Machiner y, New York, N Y, USA,
34693476. doi:10.1145/2851581.2856472
[59]
Keith Vertanen, Haythem Memmi, Justin Emge, Shyam Reyal, and Per Ola
Kristensson. 2015. Velo ciTap: Investigating Fast Mobile Text Entr y using
Sentence-Base d De co ding of Touchscre en Keyb oard Input. In
Pro ce e dings of
the 33rd Annual ACM Conference on Human Factors in Computing Systems
(CHI '15)
. Asso ciation for Computing Machiner y, New York, N Y, USA, 659668.
doi:10.1145/2702123.2702135
[60]
Yixin Wan, Ge orge Pu, Jiao Sun, Aparna Garimella, Kai-Wei Chang, and Nanyun
Peng. 2023. Kelly is a Warm Person, Joseph is a Role Mo del: Gender Biases in
LLM-Generate d Reference Letters. In
Findings of the Asso ciation for Computational
Linguistics: EMNLP 2023
, Houda Bouamor, Juan Pino, and Kalika Bali (Eds.).
Asso ciation for Computational Linguistics, Singap ore, 37303748. doi:10.18653/
v1/2023.˙ndings- emnlp.243
[61]
Jason Wei, Xuezhi Wang, Dale Schuurm ans, Maarten Bosma, Brian Ichter, Fei
Xia, Ed Chi, Quo c Le, and Denny Zhou. 2023. Chain-of-Thought Prompting
Elicits Reasoning in Large Language Mo dels. doi:10.48550/arXiv.2201.11903
arXiv:2201.11903 [cs].
[62]
Dar yl Weir, Henning Pohl, Simon Rogers, Keith Vertanen, and Per Ola Kristensson.
2014. Uncertain text entr y on mobile devices. In
Pro ce e dings of the SIGCHI
Conference on Human Factors in Computing Systems (CHI '14)
. Asso ciation for
Computing Machiner y, New York, N Y, USA, 23072316. doi:10.1145/2556288.
2557412
[63]
Liang Xu, Anqi Li, Lei Zhu, Hang Xue, Changtai Zhu, Kangkang Zhao, Haonan
He, Xuanwei Zhang, Qiyue Kang, and Zhenzhong Lan. 2023 . Sup er CLUE: A
Comprehensive Chinese Large Language Mo del Benchmark. https://ar xiv.org/
abs/2307.15020v1
[64]
An Yang, Baosong Yang, Binyuan Hui, Bo Zheng, Bowen Yu, Chang Zhou, Cheng-
p eng Li, Chengyuan Li, Dayiheng Liu, Fei Huang, Guanting Dong, Haoran Wei,
Huan Lin, Jialong Tang, Jialin Wang, Jian Yang, Jianhong Tu, Jianwei Zhang,
Jianxin Ma, Jianxin Yang, Jin Xu, Jingren Zhou, Jinze Bai, Jinzheng He, Junyang
Lin, Kai Dang, Keming Lu, Ke qin Chen, Kexin Yang, Mei Li, Mingfeng Xue,
Na Ni, Pei Zhang, Peng Wang, Ru Peng, Rui Men, Ruize Gao, Runji Lin, Shijie
Wang, Shuai Bai, Sinan Tan, Tianhang Zhu, Tianhao Li, Tianyu Liu, Wenbin Ge,
Xiao dong Deng, Xiaohuan Zhou, Xingzhang Ren, Xinyu Zhang, Xipin Wei, Xu-
ancheng Ren, Xuejing Liu, Yang Fan, Yang Yao, Yichang Zhang, Yu Wan, Yunfei
Chu, Yuqiong Liu, Zeyu Cui, Zhenru Zhang, Zhifang Guo, and Zhihao Fan. 2024.
Q wen2 Te chnical Rep ort. doi:10.48550/arXiv.2407.10671 arXiv:2407.10671.
[65]
Shumin Zhai, Michael Hunter, and Barton A. Smith. 2002. Performance Opti-
mization of Virtual Keyb oards.
HumanComputer Interaction
17, 2-3 (Sept. 2002),
229269. doi:10.1080/07370024.2002.9667315 Publisher: Taylor & Francis _eprint:
https://w w w.tandfonline.com/doi/p df/10.1080/07370024.2002.9667315.
[66]
Wayne Xin Zhao, Kun Zhou, Junyi Li, Tianyi Tang, Xiaolei Wang, Yup eng Hou,
Yingqian Min, Beichen Zhang, Junjie Zhang, Zican Dong, Yifan Du, Chen Yang,
Yushuo Chen, Zhip eng Chen, Jinhao Jiang, Ruiyang Ren, Yifan Li, Xinyu Tang,
Zikang Liu, Peiyu Liu, Jian-Yun Nie, and Ji-Rong Wen. 2023. A Sur vey of Large
Language Mo dels. doi:10.48550/arXiv.2303.18223 arXiv:2303.18223 [cs].
[67]
Chen Z hou, Zihan Yan, Ashwin Ram, Yue Gu, Yan Xiang, Can Liu, Yun Huang,
Wei Tsang Ooi, and Shengdong Zhao. 2024. GlassMail: Towards Personalise d
Wearable Assistant for On-the-Go Email Creation on Smart Glasses. In
Pro ce e dings
of the 2024 ACM Designing Interactive Systems Conference (DIS '24)
. Asso ciation
for Computing Machiner y, New York, N Y, USA, 372390. doi:10.1145/3643834.
3660683
A Example Use Cases
We present several use cases of CATIA in Figure 6. The marke d
content in re d represents the key information for text suggestion.
B Implementation Details
We present the system archite cture diagram of CATIA in Figure 7.
The system's Android mobile app communicates with the remote
ser ver via So cket.IO. When the user activates the assistant, the An-
droid app establishes communication with the ser ver, colle cts and
sends contextual information, and waits for the ser ver to exe cute
the suggestion computation results. The suggestion panel on the
Android side displays the results in real time.
For the colle ction of contextual information on the phone, re cent
scre en content is continuously up date d in the background. The app
always maintains a queue of pages from the last two minutes, which
is manage d by a scre en stability algorithm that de cides whether
to colle ct scre en text and add it to the queue. This scre en stability
algorithm takes scre enshots at regular inter vals of 200 ms and
dete cts the similarity b etwe en adjacent scre enshot images at the
pixel level. When the similarity is b elow 0.8, the scre en is considere d
to b e changing. Once the similarity exce e ds 0.8 and remains stable
for 400 ms, the scre en is considere d to have entere d a new stable
state, and the text on the scre en at that moment is colle cte d through
the accessibility ser vice and place d in the queue. In addition, if the
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
(a)
( b)
(c)
(d)
Figure 6: Four use cases of CATIA. Marke d content in re d represents the key information for text suggestion. (a) The user adde d
a new friend on a so cial me dia platform, and the friend sent a gre eting message; the assistant suggeste d suitable remarks. ( b)
The user came across a twe et ab out camera shopping and wante d to search for cameras on Go ogle; the assistant suggeste d
suitable key words. (c) The user was running on the playground when the professor sent a message asking if the user was in the
lab and re queste d him to visit their o˝ce when available; base d on the physical context ( lo cation and activity) and the message
content, the assistant suggeste d suitable resp onses. (d) The user was browsing a friend's p ost; the assistant suggeste d suitable
comments.
Figure 7: System archite cture diagram of CATIA.
text on adjacent pages in the queue has a similarity greater than
0.75, they will b e merge d into a single page.
We use
gpt-4-1106-preview
from Op enAI's Chat Completions
API as the driving LLM. The API was con˙gure d with the following
parameters:
top_p
set to 1.0,
max_tokens
limite d to 512, and the
resp onse format sp e ci˙e d as a JSON obje ct.
C Prompts
Our prompts follow a dialog format, where the system part presents
the core re quirements of each task, and the ˙rst user part provides
a detaile d instruction. Following parts contain several input-output
examples. Here we only present the prompts without their input-
output examples. For complete prompts use d in the pap er, please
refer to the supplementar y material.
C.1 Initial Suggestion
system
You are a text suggestion assistant.
user
Investigating Context-Aware Collab orative Text Entr y on Smartphones using Large Language Mo dels CHI '25, April 26May 01, 2025, Yokohama, Japan
[context] information is automatically sent following a user
'
s request for text suggestions in an input field. This
includes temporal, physical, social, and other digital
information collected on the phone (such as context.
date_time, context.location, context.screen_content,
etc.).
Among these, context.screen_content is a list of screen
pages captured in chronological order. If the type
attribute of a screen_content page is
'
chat
'
, it
represents a chat history; if it is
'
screen
'
, it
contains text snippets extracted from that particular
screen.
Your task is to deduce the user
'
s possible intents for
initiating text input and suggest appropriate texts to
the user. This involves analyzing provided context and
the active input field:
```
input_field.app: (the APP that the field is in),
input_field.label: (the label of the field),
input_field.content: (existing user input, you can infer the
attitude the user is trying to convey based on this
entered content)
```
You need to follow these steps:
1. From [context], filter out which elements are pages the
user actually want to browse. Then based on the [
input_field], further filter out which elements may be
relevant to the user
'
s input action.
2. Based on the filtered [context] from step 1 and [
input_field], analyze the [intention] (why user opens
the input field and what the user wants to express?) of
the user. Remember, since elements in context.
screen_content are the pages user browsed over a
certain period of time, so the elements (context.
screen_content[index]) with a smaller index may not be
relevant or useful, and you should pay more attention
to elements (context.screen_content[index]) with a
greater index. If there are multiple possible
intentions, list them in the keys of [
intention_suggestion_pair]. Different possible
attitudes of users under the same topic can also be
counted as multiple intentions.
3. Give [suggestion] to the user based on each [intention]
and list them in the values of [
intention_suggestion_pair].
You need to output [output.intention_suggestion_pair] in
JSON format (up to 4). Preferentially output the
intention_suggestion_pair that is more likely in the
given contexts.
C.2 Suggestion Regeneration
system
You are a text suggestion assistant and regenerate
suggestions based on history results and user
'
s
instruction.
user
[context] information is automatically sent following a user
'
s request for text suggestions in an input field. This
includes temporal, physical, social, and other digital
information collected on the phone (such as context.
date_time, context.location, context.screen_content,
etc.).
Among these, context.screen_content is a list of screen
pages captured in chronological order. If the type
attribute of a screen_content page is
'
chat
'
, it
represents a chat history; if it is
'
screen
'
, it
contains text snippets extracted from that particular
screen.
Your task is to deduce the user
'
s possible intents for
initiating text input and suggest appropriate texts to
the user. This involves analyzing provided context and
the active input field:
```
input_field.app: (the APP that the field is in),
input_field.label: (the label of the field),
input_field.content: (existing user input, you can infer the
attitude the user is trying to convey based on this
entered content)
```
You will also be provided:
- last_output: A dict of texts generated last time and the
guessed user intention.
- user_instruction: User demand for generated text.
You need to follow these steps:
1. Based on [context], [input_field], [last_output] and [
user_instruction], analyze the [intention] (why user
opens the input field and what the user wants to
express?) of the user. If there are multiple possible
intentions, list them in the keys of [
intention_suggestion_pair]. Different possible
attitudes of users under the same topic can also be
counted as multiple intentions.
2. Give [suggestion] to the user based on each [intention]
and list them in the values of [
intention_suggestion_pair].
You need to output [output.intention_suggestion_pair] in
JSON format (up to 4). Preferentially output the
intention_suggestion_pair that is more likely in the
given contexts.
C.3 Data Analysis
system
You are a text suggestion assistant. Your role is to analyze
a user
'
s smartphone text entry intention and identify
key information crucial for inferring the text a user
has input. This analysis occurs post-text entry.
user
You need to consider the contextual data captured by the
device and the groundtruth text entered by the user.
CHI '25, April 26May 01, 2025, Yokohama, Japan
Chen et al.
[context] information is captured when a user opens an input
field. This includes temporal, physical, social, and
other digital information collected on the phone (such
as context.date_time, context.location, context.
screen_content, etc.).
Among these, context.screen_content is a list of screen
pages captured in chronological order. If the type
attribute of a screen_content page is
'
chat
'
, it
represents a chat history; if it is
'
screen
'
, it
contains text snippets extracted from that particular
screen.
[input_field] is the target input field, which contains the
following attributes:
- input_field.app: the APP that the field is in,
- input_field.label: a label providing a description of the
field
'
s function or purpose,
- input_field.content: existing user input.
[entered_text] is the final text entered by the user in the
current context.
You need to follow these steps:
1. Analyze the [intention] (why the user opens the input
field and what the user wants to express?) of the user
based on [context], [input_field] and [entered_text].
2. Identify the [key_information] (elements in [context] and
[input_field], not in [entered_text]) that is crucial
to infer the [entered_text]. Focus on the most relevant
details that directly influence the user
'
s text entry
process. If the user uses only part of context.
screen_content, you only need to output the part of the
content that is used.
You need to output [output.intention], [output.
key_information] in JSON format.
