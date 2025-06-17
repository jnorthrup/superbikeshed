<div align="center">
<sub>

English • [Català](locales/ca/README.md) • [Deutsch](locales/de/README.md) • [Español](locales/es/README.md) • [Français](locales/fr/README.md) • [हिन्दी](locales/hi/README.md) • [Italiano](locales/it/README.md) • [Nederlands](locales/nl/README.md) • [Русский](locales/ru/README.md)

</sub>
<sub>

[日本語](locales/ja/README.md) • [한국어](locales/ko/README.md) • [Polski](locales/pl/README.md) • [Português (BR)](locales/pt-BR/README.md) • [Türkçe](locales/tr/README.md) • [Tiếng Việt](locales/vi/README.md) • [简体中文](locales/zh-CN/README.md) • [繁體中文](locales/zh-TW/README.md)

</sub>
</div>
<br>
<div align="center">
  <h1>Roo Code (prev. Roo Cline)</h1>
  <p align="center">
  <img src="https://media.githubusercontent.com/media/RooCodeInc/Roo-Code/main/src/assets/docs/demo.gif" width="100%" />
  </p>
  <p>Connect with developers, contribute ideas, and stay ahead with the latest AI-powered coding tools.</p>
  
  <a href="https://discord.gg/roocode" target="_blank"><img src="https://img.shields.io/badge/Join%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join Discord"></a>
  <a href="https://www.reddit.com/r/RooCode/" target="_blank"><img src="https://img.shields.io/badge/Join%20Reddit-FF4500?style=for-the-badge&logo=reddit&logoColor=white" alt="Join Reddit"></a>
  
</div>
<br>
<br>

<div align="center">

<a href="https://marketplace.visualstudio.com/items?itemName=RooVeterinaryInc.roo-cline" target="_blank"><img src="https://img.shields.io/badge/Download%20on%20VS%20Marketplace-blue?style=for-the-badge&logo=visualstudiocode&logoColor=white" alt="Download on VS Marketplace"></a>
<a href="https://github.com/RooCodeInc/Roo-Code/discussions/categories/feature-requests?discussions_q=is%3Aopen+category%3A%22Feature+Requests%22+sort%3Atop" target="_blank"><img src="https://img.shields.io/badge/Feature%20Requests-yellow?style=for-the-badge" alt="Feature Requests"></a>
<a href="https://marketplace.visualstudio.com/items?itemName=RooVeterinaryInc.roo-cline&ssr=false#review-details" target="_blank"><img src="https://img.shields.io/badge/Rate%20%26%20Review-green?style=for-the-badge" alt="Rate & Review"></a>
<a href="https://docs.roocode.com" target="_blank"><img src="https://img.shields.io/badge/Documentation-6B46C1?style=for-the-badge&logo=readthedocs&logoColor=white" alt="Documentation"></a>

</div>

**Roo Code** is an AI-powered **autonomous coding agent** that lives in your editor. It can:

- Communicate in natural language
- Read and write files directly in your workspace
- Run terminal commands
- Automate browser actions
- Integrate with any OpenAI-compatible or custom API/model
- Adapt its “personality” and capabilities through **Custom Modes**

Whether you’re seeking a flexible coding partner, a system architect, or specialized roles like a QA engineer or product manager, Roo Code can help you build software more efficiently.

Check out the [CHANGELOG](CHANGELOG.md) for detailed updates and fixes.

---

## 🎉 Roo Code 3.19 Released

Roo Code 3.19 brings intelligent context management improvements and enhanced user experience!

- **Intelligent Context Condensing Enabled by Default** - Context condensing is now enabled by default with configurable settings for when automatic condensing happens.
- **Manual Condensing Button** - New button in the task header allows you to manually trigger context condensing at any time.
- **Enhanced Condensing Settings** - Fine-tune when and how automatic condensing occurs through the Context Settings panel.

---

## What Can Roo Code Do?

- 🚀 **Generate Code** from natural language descriptions
- 🔧 **Refactor & Debug** existing code
- 📝 **Write & Update** documentation
- 🤔 **Answer Questions** about your codebase
- 🔄 **Automate** repetitive tasks
- 🏗️ **Create** new files and projects

## Quick Start

1. [Install Roo Code](https://docs.roocode.com/getting-started/installing)
2. [Connect Your AI Provider](https://docs.roocode.com/getting-started/connecting-api-provider)
3. [Try Your First Task](https://docs.roocode.com/getting-started/your-first-task)

## Key Features

### Multiple Modes

Roo Code adapts to your needs with specialized [modes](https://docs.roocode.com/basic-usage/using-modes):

- **Code Mode:** For general-purpose coding tasks
- **Architect Mode:** For planning and technical leadership
- **Ask Mode:** For answering questions and providing information
- **Debug Mode:** For systematic problem diagnosis
- **[Custom Modes](https://docs.roocode.com/advanced-usage/custom-modes):** Create unlimited specialized personas for security auditing, performance optimization, documentation, or any other task

### Smart Tools

Roo Code comes with powerful [tools](https://docs.roocode.com/basic-usage/how-tools-work) that can:

- Read and write files in your project
- Execute commands in your VS Code terminal
- Control a web browser
- Use external tools via [MCP (Model Context Protocol)](https://docs.roocode.com/advanced-usage/mcp)

MCP extends Roo Code's capabilities by allowing you to add unlimited custom tools. Integrate with external APIs, connect to databases, or create specialized development tools - MCP provides the framework to expand Roo Code's functionality to meet your specific needs.

## Project Showcase: Leveraging Kotlin Scripting with k2script

This repository hosts several interconnected Kotlin-based projects that demonstrate the power and flexibility of Kotlin for various applications, from scripting to financial technology. A key enabler for these projects is `k2script`, an advanced Kotlin scripting solution.

### Core Components:

*   **`k2script` ([k2script/](k2script/))**: An enhanced scripting tool for Kotlin that acts as a powerful wrapper around the `kotlinc` compiler. `k2script` significantly improves upon native Kotlin scripting by providing features such as:
    *   **Simplified Dependency Management**: Declare dependencies directly in your scripts using `@file:DependsOn` with Gradle-style locators (e.g., as seen in `moneyfan/coinbaseXChangeBot.main.kts`).
    *   **Compiled Script Caching**: For faster execution on subsequent runs.
    *   **Versatile Script Inputs**: Supports interpreter mode, execution from stdin, local files, or URLs.
    *   **Standalone Deployment**: Package scripts into self-contained binaries for easy distribution.
    *   **Modern Kotlin Support**: Updated for Kotlin 2 and the latest scripting APIs, ensuring access to current language features.
    `k2script` effectively replaces and modernizes older approaches to Kotlin scripting, making it a robust choice for developing and running Kotlin scripts.

*   **`ta4k` ([ta4k/](ta4k/))**: A project focused on financial market data. It provides tools for fetching, storing, and processing klines (candlestick data), laying the groundwork for technical analysis and backtesting trading strategies. A core concept within `ta4k` is "TrikeShed / columnar," representing a functional, immutable data layer.

*   **`moneyfan` ([moneyfan/](moneyfan/))**: This project features `coinbaseXChangeBot.main.kts`, an automated trading bot designed for the Coinbase exchange. It demonstrates a practical application of Kotlin for financial technology.

### Synergy and Benefits:

The `moneyfan` trading bot (`.kts` script) exemplifies how these components work together:

1.  **Scripting Foundation**: `moneyfan` is a Kotlin script that leverages `k2script` for its execution. `k2script` manages its numerous dependencies (like XChange for exchange interaction, Kotlinx coroutines, and serialization) seamlessly.
2.  **Data Integration**: `moneyfan` incorporates core data structure definitions ad-hoc from `TrikeShed` (found within `ta4k`), showcasing how applications can consume the financial data processing capabilities developed in `ta4k`.
3.  **Development and Deployment**:
    *   Developers working on `moneyfan` or similar scripts benefit from `k2script`'s features like REPL integration for interactive testing and the ability to bootstrap an IDEA project for a better development experience.
    *   Once developed, `moneyfan` could be packaged as a standalone binary using `k2script`, simplifying its deployment and execution in various environments.

In essence, `k2script` provides the scripting power and convenience that enables the development and execution of sophisticated Kotlin applications like `moneyfan`, which in turn can build upon specialized libraries and data layers like those provided by `ta4k`. This ecosystem highlights the advantages of using Kotlin for both general-purpose scripting and domain-specific problems.

### Customization

Make Roo Code work your way with:

- [Custom Instructions](https://docs.roocode.com/advanced-usage/custom-instructions) for personalized behavior
- [Custom Modes](https://docs.roocode.com/advanced-usage/custom-modes) for specialized tasks
- [Local Models](https://docs.roocode.com/advanced-usage/local-models) for offline use
- [Auto-Approval Settings](https://docs.roocode.com/advanced-usage/auto-approving-actions) for faster workflows

## Resources

### Documentation

- [Basic Usage Guide](https://docs.roocode.com/basic-usage/the-chat-interface)
- [Advanced Features](https://docs.roocode.com/advanced-usage/auto-approving-actions)
- [Frequently Asked Questions](https://docs.roocode.com/faq)

### Community

- **Discord:** [Join our Discord server](https://discord.gg/roocode) for real-time help and discussions
- **Reddit:** [Visit our subreddit](https://www.reddit.com/r/RooCode) to share experiences and tips
- **GitHub:** Report [issues](https://github.com/RooCodeInc/Roo-Code/issues) or request [features](https://github.com/RooCodeInc/Roo-Code/discussions/categories/feature-requests?discussions_q=is%3Aopen+category%3A%22Feature+Requests%22+sort%3Atop)

---

## Local Setup & Development

1. **Clone** the repo:

```sh
git clone https://github.com/RooCodeInc/Roo-Code.git
```

2. **Install dependencies**:

```sh
pnpm install
```

3. **Run the extension**:

Press `F5` (or **Run** → **Start Debugging**) in VSCode to open a new window with Roo Code running.

Changes to the webview will appear immediately. Changes to the core extension will require a restart of the extension host.

Alternatively you can build a .vsix and install it directly in VSCode:

```sh
pnpm vsix
```

A `.vsix` file will appear in the `bin/` directory which can be installed with:

```sh
code --install-extension bin/roo-cline-<version>.vsix
```

We use [changesets](https://github.com/changesets/changesets) for versioning and publishing. Check our `CHANGELOG.md` for release notes.

---

## Disclaimer

**Please note** that Roo Code, Inc does **not** make any representations or warranties regarding any code, models, or other tools provided or made available in connection with Roo Code, any associated third-party tools, or any resulting outputs. You assume **all risks** associated with the use of any such tools or outputs; such tools are provided on an **"AS IS"** and **"AS AVAILABLE"** basis. Such risks may include, without limitation, intellectual property infringement, cyber vulnerabilities or attacks, bias, inaccuracies, errors, defects, viruses, downtime, property loss or damage, and/or personal injury. You are solely responsible for your use of any such tools or outputs (including, without limitation, the legality, appropriateness, and results thereof).

---

## Contributing

We love community contributions! Get started by reading our [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Contributors

Thanks to all our contributors who have helped make Roo Code better!

<!-- START CONTRIBUTORS SECTION - AUTO-GENERATED, DO NOT EDIT MANUALLY -->

|                     <a href="https://github.com/mrubens"><img src="https://avatars.githubusercontent.com/u/2600?v=4" width="100" height="100" alt="mrubens"/><br /><sub><b>mrubens</b></sub></a>                      |    <a href="https://github.com/saoudrizwan"><img src="https://avatars.githubusercontent.com/u/7799382?v=4" width="100" height="100" alt="saoudrizwan"/><br /><sub><b>saoudrizwan</b></sub></a>    |                    <a href="https://github.com/cte"><img src="https://avatars.githubusercontent.com/u/16332?v=4" width="100" height="100" alt="cte"/><br /><sub><b>cte</b></sub></a>                     |          <a href="https://github.com/samhvw8"><img src="https://avatars.githubusercontent.com/u/12538214?v=4" width="100" height="100" alt="samhvw8"/><br /><sub><b>samhvw8</b></sub></a>           |          <a href="https://github.com/daniel-lxs"><img src="https://avatars.githubusercontent.com/u/57051444?v=4" width="100" height="100" alt="daniel-lxs"/><br /><sub><b>daniel-lxs</b></sub></a>          |            <a href="https://github.com/a8trejo"><img src="https://avatars.githubusercontent.com/u/62401433?v=4" width="100" height="100" alt="a8trejo"/><br /><sub><b>a8trejo</b></sub></a>            |
| :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: |
|                     <a href="https://github.com/KJ7LNW"><img src="https://avatars.githubusercontent.com/u/93454819?v=4" width="100" height="100" alt="KJ7LNW"/><br /><sub><b>KJ7LNW</b></sub></a>                     |    <a href="https://github.com/ColemanRoo"><img src="https://avatars.githubusercontent.com/u/117104599?v=4" width="100" height="100" alt="ColemanRoo"/><br /><sub><b>ColemanRoo</b></sub></a>     |       <a href="https://github.com/canrobins13"><img src="https://avatars.githubusercontent.com/u/20544372?v=4" width="100" height="100" alt="canrobins13"/><br /><sub><b>canrobins13</b></sub></a>       | <a href="https://github.com/hannesrudolph"><img src="https://avatars.githubusercontent.com/u/49103247?v=4" width="100" height="100" alt="hannesrudolph"/><br /><sub><b>hannesrudolph</b></sub></a>  |             <a href="https://github.com/stea9499"><img src="https://avatars.githubusercontent.com/u/4163795?v=4" width="100" height="100" alt="stea9499"/><br /><sub><b>stea9499</b></sub></a>              |     <a href="https://github.com/joemanley201"><img src="https://avatars.githubusercontent.com/u/8299960?v=4" width="100" height="100" alt="joemanley201"/><br /><sub><b>joemanley201</b></sub></a>     |
|                <a href="https://github.com/System233"><img src="https://avatars.githubusercontent.com/u/20336040?v=4" width="100" height="100" alt="System233"/><br /><sub><b>System233</b></sub></a>                 |    <a href="https://github.com/nissa-seru"><img src="https://avatars.githubusercontent.com/u/119150866?v=4" width="100" height="100" alt="nissa-seru"/><br /><sub><b>nissa-seru</b></sub></a>     |           <a href="https://github.com/jquanton"><img src="https://avatars.githubusercontent.com/u/88576563?v=4" width="100" height="100" alt="jquanton"/><br /><sub><b>jquanton</b></sub></a>            |            <a href="https://github.com/NyxJae"><img src="https://avatars.githubusercontent.com/u/52313587?v=4" width="100" height="100" alt="NyxJae"/><br /><sub><b>NyxJae</b></sub></a>            |             <a href="https://github.com/MuriloFP"><img src="https://avatars.githubusercontent.com/u/50873657?v=4" width="100" height="100" alt="MuriloFP"/><br /><sub><b>MuriloFP</b></sub></a>             |               <a href="https://github.com/d-oit"><img src="https://avatars.githubusercontent.com/u/6849456?v=4" width="100" height="100" alt="d-oit"/><br /><sub><b>d-oit</b></sub></a>                |
|                 <a href="https://github.com/punkpeye"><img src="https://avatars.githubusercontent.com/u/108313943?v=4" width="100" height="100" alt="punkpeye"/><br /><sub><b>punkpeye</b></sub></a>                  |                   <a href="https://github.com/jr"><img src="https://avatars.githubusercontent.com/u/5629?v=4" width="100" height="100" alt="jr"/><br /><sub><b>jr</b></sub></a>                   |         <a href="https://github.com/wkordalski"><img src="https://avatars.githubusercontent.com/u/3035587?v=4" width="100" height="100" alt="wkordalski"/><br /><sub><b>wkordalski</b></sub></a>         |         <a href="https://github.com/elianiva"><img src="https://avatars.githubusercontent.com/u/51877647?v=4" width="100" height="100" alt="elianiva"/><br /><sub><b>elianiva</b></sub></a>         |       <a href="https://github.com/monotykamary"><img src="https://avatars.githubusercontent.com/u/1130103?v=4" width="100" height="100" alt="monotykamary"/><br /><sub><b>monotykamary</b></sub></a>        |            <a href="https://github.com/cannuri"><img src="https://avatars.githubusercontent.com/u/91494156?v=4" width="100" height="100" alt="cannuri"/><br /><sub><b>cannuri</b></sub></a>            |
| <a href="https://github.com/Smartsheet-JB-Brown"><img src="https://avatars.githubusercontent.com/u/171734120?v=4" width="100" height="100" alt="Smartsheet-JB-Brown"/><br /><sub><b>Smartsheet-JB-Brown</b></sub></a> | <a href="https://github.com/zhangtony239"><img src="https://avatars.githubusercontent.com/u/157202938?v=4" width="100" height="100" alt="zhangtony239"/><br /><sub><b>zhangtony239</b></sub></a>  |         <a href="https://github.com/sachasayan"><img src="https://avatars.githubusercontent.com/u/1666034?v=4" width="100" height="100" alt="sachasayan"/><br /><sub><b>sachasayan</b></sub></a>         |        <a href="https://github.com/xyOz-dev"><img src="https://avatars.githubusercontent.com/u/195602624?v=4" width="100" height="100" alt="xyOz-dev"/><br /><sub><b>xyOz-dev</b></sub></a>         |           <a href="https://github.com/feifei325"><img src="https://avatars.githubusercontent.com/u/46489071?v=4" width="100" height="100" alt="feifei325"/><br /><sub><b>feifei325</b></sub></a>            |               <a href="https://github.com/qdaxb"><img src="https://avatars.githubusercontent.com/u/4157870?v=4" width="100" height="100" alt="qdaxb"/><br /><sub><b>qdaxb</b></sub></a>                |
|      <a href="https://github.com/vigneshsubbiah16"><img src="https://avatars.githubusercontent.com/u/51325334?v=4" width="100" height="100" alt="vigneshsubbiah16"/><br /><sub><b>vigneshsubbiah16</b></sub></a>      |   <a href="https://github.com/shariqriazz"><img src="https://avatars.githubusercontent.com/u/196900129?v=4" width="100" height="100" alt="shariqriazz"/><br /><sub><b>shariqriazz</b></sub></a>   |         <a href="https://github.com/lloydchang"><img src="https://avatars.githubusercontent.com/u/1329685?v=4" width="100" height="100" alt="lloydchang"/><br /><sub><b>lloydchang</b></sub></a>         | <a href="https://github.com/pugazhendhi-m"><img src="https://avatars.githubusercontent.com/u/132246623?v=4" width="100" height="100" alt="pugazhendhi-m"/><br /><sub><b>pugazhendhi-m</b></sub></a> |               <a href="https://github.com/Szpadel"><img src="https://avatars.githubusercontent.com/u/1857251?v=4" width="100" height="100" alt="Szpadel"/><br /><sub><b>Szpadel</b></sub></a>               |           <a href="https://github.com/dtrugman"><img src="https://avatars.githubusercontent.com/u/2451669?v=4" width="100" height="100" alt="dtrugman"/><br /><sub><b>dtrugman</b></sub></a>           |
|      <a href="https://github.com/diarmidmackenzie"><img src="https://avatars.githubusercontent.com/u/16045703?v=4" width="100" height="100" alt="diarmidmackenzie"/><br /><sub><b>diarmidmackenzie</b></sub></a>      |         <a href="https://github.com/psv2522"><img src="https://avatars.githubusercontent.com/u/87223770?v=4" width="100" height="100" alt="psv2522"/><br /><sub><b>psv2522</b></sub></a>          |           <a href="https://github.com/Premshay"><img src="https://avatars.githubusercontent.com/u/28099628?v=4" width="100" height="100" alt="Premshay"/><br /><sub><b>Premshay</b></sub></a>            |       <a href="https://github.com/lupuletic"><img src="https://avatars.githubusercontent.com/u/105351510?v=4" width="100" height="100" alt="lupuletic"/><br /><sub><b>lupuletic</b></sub></a>       |                <a href="https://github.com/aheizi"><img src="https://avatars.githubusercontent.com/u/8243770?v=4" width="100" height="100" alt="aheizi"/><br /><sub><b>aheizi</b></sub></a>                 |  <a href="https://github.com/PeterDaveHello"><img src="https://avatars.githubusercontent.com/u/3691490?v=4" width="100" height="100" alt="PeterDaveHello"/><br /><sub><b>PeterDaveHello</b></sub></a>  |
|             <a href="https://github.com/olweraltuve"><img src="https://avatars.githubusercontent.com/u/39308405?v=4" width="100" height="100" alt="olweraltuve"/><br /><sub><b>olweraltuve</b></sub></a>              |        <a href="https://github.com/ChuKhaLi"><img src="https://avatars.githubusercontent.com/u/15166543?v=4" width="100" height="100" alt="ChuKhaLi"/><br /><sub><b>ChuKhaLi</b></sub></a>        | <a href="https://github.com/nbihan-mediware"><img src="https://avatars.githubusercontent.com/u/42357253?v=4" width="100" height="100" alt="nbihan-mediware"/><br /><sub><b>nbihan-mediware</b></sub></a> |       <a href="https://github.com/RaySinner"><img src="https://avatars.githubusercontent.com/u/118297374?v=4" width="100" height="100" alt="RaySinner"/><br /><sub><b>RaySinner</b></sub></a>       |                <a href="https://github.com/kiwina"><img src="https://avatars.githubusercontent.com/u/1071364?v=4" width="100" height="100" alt="kiwina"/><br /><sub><b>kiwina</b></sub></a>                 |     <a href="https://github.com/afshawnlotfi"><img src="https://avatars.githubusercontent.com/u/6283745?v=4" width="100" height="100" alt="afshawnlotfi"/><br /><sub><b>afshawnlotfi</b></sub></a>     |
|                      <a href="https://github.com/pdecat"><img src="https://avatars.githubusercontent.com/u/318490?v=4" width="100" height="100" alt="pdecat"/><br /><sub><b>pdecat</b></sub></a>                      | <a href="https://github.com/noritaka1166"><img src="https://avatars.githubusercontent.com/u/189505037?v=4" width="100" height="100" alt="noritaka1166"/><br /><sub><b>noritaka1166</b></sub></a>  |          <a href="https://github.com/kyle-apex"><img src="https://avatars.githubusercontent.com/u/20145331?v=4" width="100" height="100" alt="kyle-apex"/><br /><sub><b>kyle-apex</b></sub></a>          |          <a href="https://github.com/emshvac"><img src="https://avatars.githubusercontent.com/u/121588911?v=4" width="100" height="100" alt="emshvac"/><br /><sub><b>emshvac</b></sub></a>          |        <a href="https://github.com/chrarnoldus"><img src="https://avatars.githubusercontent.com/u/12196001?v=4" width="100" height="100" alt="chrarnoldus"/><br /><sub><b>chrarnoldus</b></sub></a>         |         <a href="https://github.com/Lunchb0ne"><img src="https://avatars.githubusercontent.com/u/22198661?v=4" width="100" height="100" alt="Lunchb0ne"/><br /><sub><b>Lunchb0ne</b></sub></a>         |
|               <a href="https://github.com/SmartManoj"><img src="https://avatars.githubusercontent.com/u/7231077?v=4" width="100" height="100" alt="SmartManoj"/><br /><sub><b>SmartManoj</b></sub></a>                |        <a href="https://github.com/vagadiya"><img src="https://avatars.githubusercontent.com/u/32499123?v=4" width="100" height="100" alt="vagadiya"/><br /><sub><b>vagadiya</b></sub></a>        |     <a href="https://github.com/slytechnical"><img src="https://avatars.githubusercontent.com/u/139649758?v=4" width="100" height="100" alt="slytechnical"/><br /><sub><b>slytechnical</b></sub></a>     | <a href="https://github.com/arthurauffray"><img src="https://avatars.githubusercontent.com/u/51604173?v=4" width="100" height="100" alt="arthurauffray"/><br /><sub><b>arthurauffray</b></sub></a>  |               <a href="https://github.com/upamune"><img src="https://avatars.githubusercontent.com/u/8219560?v=4" width="100" height="100" alt="upamune"/><br /><sub><b>upamune</b></sub></a>               |    <a href="https://github.com/StevenTCramer"><img src="https://avatars.githubusercontent.com/u/357219?v=4" width="100" height="100" alt="StevenTCramer"/><br /><sub><b>StevenTCramer</b></sub></a>    |
|                      <a href="https://github.com/sammcj"><img src="https://avatars.githubusercontent.com/u/862951?v=4" width="100" height="100" alt="sammcj"/><br /><sub><b>sammcj</b></sub></a>                      |           <a href="https://github.com/p12tic"><img src="https://avatars.githubusercontent.com/u/1056711?v=4" width="100" height="100" alt="p12tic"/><br /><sub><b>p12tic</b></sub></a>            |              <a href="https://github.com/gtaylor"><img src="https://avatars.githubusercontent.com/u/75556?v=4" width="100" height="100" alt="gtaylor"/><br /><sub><b>gtaylor</b></sub></a>               |        <a href="https://github.com/aitoroses"><img src="https://avatars.githubusercontent.com/u/1699368?v=4" width="100" height="100" alt="aitoroses"/><br /><sub><b>aitoroses</b></sub></a>        |      <a href="https://github.com/mr-ryan-james"><img src="https://avatars.githubusercontent.com/u/9344431?v=4" width="100" height="100" alt="mr-ryan-james"/><br /><sub><b>mr-ryan-james</b></sub></a>      |            <a href="https://github.com/heyseth"><img src="https://avatars.githubusercontent.com/u/8293842?v=4" width="100" height="100" alt="heyseth"/><br /><sub><b>heyseth</b></sub></a>             |
|                 <a href="https://github.com/taisukeoe"><img src="https://avatars.githubusercontent.com/u/1506707?v=4" width="100" height="100" alt="taisukeoe"/><br /><sub><b>taisukeoe</b></sub></a>                 | <a href="https://github.com/taylorwilsdon"><img src="https://avatars.githubusercontent.com/u/6508528?v=4" width="100" height="100" alt="taylorwilsdon"/><br /><sub><b>taylorwilsdon</b></sub></a> |             <a href="https://github.com/NamesMT"><img src="https://avatars.githubusercontent.com/u/23612546?v=4" width="100" height="100" alt="NamesMT"/><br /><sub><b>NamesMT</b></sub></a>             |               <a href="https://github.com/avtc"><img src="https://avatars.githubusercontent.com/u/10050240?v=4" width="100" height="100" alt="avtc"/><br /><sub><b>avtc</b></sub></a>               |          <a href="https://github.com/dlab-anton"><img src="https://avatars.githubusercontent.com/u/20571486?v=4" width="100" height="100" alt="dlab-anton"/><br /><sub><b>dlab-anton</b></sub></a>          |              <a href="https://github.com/eonghk"><img src="https://avatars.githubusercontent.com/u/139964?v=4" width="100" height="100" alt="eonghk"/><br /><sub><b>eonghk</b></sub></a>               |
|                  <a href="https://github.com/ronyblum"><img src="https://avatars.githubusercontent.com/u/20314054?v=4" width="100" height="100" alt="ronyblum"/><br /><sub><b>ronyblum</b></sub></a>                  |      <a href="https://github.com/teddyOOXX"><img src="https://avatars.githubusercontent.com/u/121077180?v=4" width="100" height="100" alt="teddyOOXX"/><br /><sub><b>teddyOOXX</b></sub></a>      |       <a href="https://github.com/vincentsong"><img src="https://avatars.githubusercontent.com/u/2343574?v=4" width="100" height="100" alt="vincentsong"/><br /><sub><b>vincentsong</b></sub></a>        |          <a href="https://github.com/yongjer"><img src="https://avatars.githubusercontent.com/u/54315206?v=4" width="100" height="100" alt="yongjer"/><br /><sub><b>yongjer</b></sub></a>           |           <a href="https://github.com/zeozeozeo"><img src="https://avatars.githubusercontent.com/u/108888572?v=4" width="100" height="100" alt="zeozeozeo"/><br /><sub><b>zeozeozeo</b></sub></a>           |              <a href="https://github.com/ashktn"><img src="https://avatars.githubusercontent.com/u/6723913?v=4" width="100" height="100" alt="ashktn"/><br /><sub><b>ashktn</b></sub></a>              |
|                    <a href="https://github.com/franekp"><img src="https://avatars.githubusercontent.com/u/9804230?v=4" width="100" height="100" alt="franekp"/><br /><sub><b>franekp</b></sub></a>                    |        <a href="https://github.com/yt3trees"><img src="https://avatars.githubusercontent.com/u/57471763?v=4" width="100" height="100" alt="yt3trees"/><br /><sub><b>yt3trees</b></sub></a>        |        <a href="https://github.com/anton-otee"><img src="https://avatars.githubusercontent.com/u/149477749?v=4" width="100" height="100" alt="anton-otee"/><br /><sub><b>anton-otee</b></sub></a>        |        <a href="https://github.com/benzntech"><img src="https://avatars.githubusercontent.com/u/4044180?v=4" width="100" height="100" alt="benzntech"/><br /><sub><b>benzntech</b></sub></a>        |          <a href="https://github.com/axkirillov"><img src="https://avatars.githubusercontent.com/u/32141102?v=4" width="100" height="100" alt="axkirillov"/><br /><sub><b>axkirillov</b></sub></a>          |          <a href="https://github.com/bramburn"><img src="https://avatars.githubusercontent.com/u/11090413?v=4" width="100" height="100" alt="bramburn"/><br /><sub><b>bramburn</b></sub></a>           |
|                  <a href="https://github.com/hassoncs"><img src="https://avatars.githubusercontent.com/u/5104925?v=4" width="100" height="100" alt="hassoncs"/><br /><sub><b>hassoncs</b></sub></a>                   |        <a href="https://github.com/snoyiatk"><img src="https://avatars.githubusercontent.com/u/3056569?v=4" width="100" height="100" alt="snoyiatk"/><br /><sub><b>snoyiatk</b></sub></a>         |     <a href="https://github.com/GitlyHallows"><img src="https://avatars.githubusercontent.com/u/136527758?v=4" width="100" height="100" alt="GitlyHallows"/><br /><sub><b>GitlyHallows</b></sub></a>     |            <a href="https://github.com/jcbdev"><img src="https://avatars.githubusercontent.com/u/17152092?v=4" width="100" height="100" alt="jcbdev"/><br /><sub><b>jcbdev</b></sub></a>            |    <a href="https://github.com/Chenjiayuan195"><img src="https://avatars.githubusercontent.com/u/30591313?v=4" width="100" height="100" alt="Chenjiayuan195"/><br /><sub><b>Chenjiayuan195</b></sub></a>    |          <a href="https://github.com/julionav"><img src="https://avatars.githubusercontent.com/u/45607850?v=4" width="100" height="100" alt="julionav"/><br /><sub><b>julionav</b></sub></a>           |
|               <a href="https://github.com/SplittyDev"><img src="https://avatars.githubusercontent.com/u/4216049?v=4" width="100" height="100" alt="SplittyDev"/><br /><sub><b>SplittyDev</b></sub></a>                |                 <a href="https://github.com/mdp"><img src="https://avatars.githubusercontent.com/u/2868?v=4" width="100" height="100" alt="mdp"/><br /><sub><b>mdp</b></sub></a>                  |               <a href="https://github.com/napter"><img src="https://avatars.githubusercontent.com/u/6260841?v=4" width="100" height="100" alt="napter"/><br /><sub><b>napter</b></sub></a>               |            <a href="https://github.com/Ruakij"><img src="https://avatars.githubusercontent.com/u/54639830?v=4" width="100" height="100" alt="Ruakij"/><br /><sub><b>Ruakij</b></sub></a>            |                    <a href="https://github.com/ross"><img src="https://avatars.githubusercontent.com/u/12789?v=4" width="100" height="100" alt="ross"/><br /><sub><b>ross</b></sub></a>                     |           <a href="https://github.com/philfung"><img src="https://avatars.githubusercontent.com/u/1054593?v=4" width="100" height="100" alt="philfung"/><br /><sub><b>philfung</b></sub></a>           |
|               <a href="https://github.com/GOODBOY008"><img src="https://avatars.githubusercontent.com/u/13617900?v=4" width="100" height="100" alt="GOODBOY008"/><br /><sub><b>GOODBOY008</b></sub></a>               |         <a href="https://github.com/hatsu38"><img src="https://avatars.githubusercontent.com/u/16137809?v=4" width="100" height="100" alt="hatsu38"/><br /><sub><b>hatsu38</b></sub></a>          |             <a href="https://github.com/hongzio"><img src="https
