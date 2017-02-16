console.log('REPL', location.search);
if (location.search === '?repl') {
    document.write('<script type="text/javascript" src="repl.js"></script>')
} else {
    document.write('<script type="text/javascript" src="main.js"></script>')
}
