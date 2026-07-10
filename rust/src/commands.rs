#[derive(Clone, Copy)]
pub struct CommandHint {
    pub name: &'static str,
    pub usage: &'static str,
}

const COMMANDS: &[CommandHint] = &[
    CommandHint { name: "advancement", usage: " (grant|revoke)" },
    CommandHint { name: "attribute", usage: " <target> <attribute> (get|base|modifier)" },
    CommandHint { name: "execute", usage: " (run|if|unless|as|at|store|positioned|rotated|facing|align|anchored|in|summon|on)" },
    CommandHint { name: "bossbar", usage: " (add|remove|list|set|get)" },
    CommandHint { name: "clear", usage: " [<targets>]" },
    CommandHint { name: "clone", usage: " (<begin>|from)" },
    CommandHint { name: "damage", usage: " <target> <amount> [<damageType>]" },
    CommandHint { name: "data", usage: " (merge|get|remove|modify)" },
    CommandHint { name: "datapack", usage: " (enable|disable|list|create)" },
    CommandHint { name: "debug", usage: " (start|stop|function)" },
    CommandHint { name: "defaultgamemode", usage: " <gamemode>" },
    CommandHint { name: "dialog", usage: " (show|clear)" },
    CommandHint { name: "difficulty", usage: " [peaceful|easy|normal|hard]" },
    CommandHint { name: "effect", usage: " (clear|give)" },
    CommandHint { name: "me", usage: " <action>" },
    CommandHint { name: "enchant", usage: " <targets> <enchantment> [<level>]" },
    CommandHint { name: "experience", usage: " (add|set|query)" },
    CommandHint { name: "xp", usage: " -> experience" },
    CommandHint { name: "fill", usage: " <from> <to> <block> [outline|hollow|destroy|strict|replace|keep]" },
    CommandHint { name: "fillbiome", usage: " <from> <to> <biome> [replace]" },
    CommandHint { name: "forceload", usage: " (add|remove|query)" },
    CommandHint { name: "function", usage: " <name> [<arguments>|with]" },
    CommandHint { name: "gamemode", usage: " <gamemode> [<target>]" },
    CommandHint { name: "gamerule", usage: " <rule>" },
    CommandHint { name: "give", usage: " <targets> <item> [<count>]" },
    CommandHint { name: "help", usage: " [<command>]" },
    CommandHint { name: "item", usage: " (replace|modify)" },
    CommandHint { name: "kick", usage: " <targets> [<reason>]" },
    CommandHint { name: "kill", usage: " [<targets>]" },
    CommandHint { name: "list", usage: " [uuids]" },
    CommandHint { name: "locate", usage: " (structure|biome|poi)" },
    CommandHint { name: "loot", usage: " (replace|insert|give|spawn)" },
    CommandHint { name: "msg", usage: " <targets> <message>" },
    CommandHint { name: "tell", usage: " -> msg" },
    CommandHint { name: "w", usage: " -> msg" },
    CommandHint { name: "swing", usage: " [<targets>]" },
    CommandHint { name: "particle", usage: " <name> [<pos>]" },
    CommandHint { name: "place", usage: " (feature|jigsaw|structure|template)" },
    CommandHint { name: "playsound", usage: " <sound> [master|music|record|weather|block|hostile|neutral|player|ambient|voice|ui]" },
    CommandHint { name: "random", usage: " (value|roll|reset)" },
    CommandHint { name: "reload", usage: "" },
    CommandHint { name: "recipe", usage: " (give|take)" },
    CommandHint { name: "fetchprofile", usage: " (name|id|entity)" },
    CommandHint { name: "return", usage: " (<value>|fail|run)" },
    CommandHint { name: "ride", usage: " <target> (mount|dismount)" },
    CommandHint { name: "rotate", usage: " <target> (<rotation>|facing)" },
    CommandHint { name: "say", usage: " <message>" },
    CommandHint { name: "schedule", usage: " (function|clear)" },
    CommandHint { name: "scoreboard", usage: " (objectives|players)" },
    CommandHint { name: "seed", usage: "" },
    CommandHint { name: "version", usage: "" },
    CommandHint { name: "setblock", usage: " <pos> <block> [destroy|keep|replace|strict]" },
    CommandHint { name: "spawnpoint", usage: " [<targets>]" },
    CommandHint { name: "setworldspawn", usage: " [<pos>]" },
    CommandHint { name: "spectate", usage: " [<target>]" },
    CommandHint { name: "spreadplayers", usage: " <center> <spreadDistance> <maxRange> (<respectTeams>|under)" },
    CommandHint { name: "stopsound", usage: " <targets> [*|master|music|record|weather|block|hostile|neutral|player|ambient|voice|ui]" },
    CommandHint { name: "stopwatch", usage: " (create|query|restart|remove)" },
    CommandHint { name: "summon", usage: " <entity> [<pos>]" },
    CommandHint { name: "tag", usage: " <targets> (add|remove|list)" },
    CommandHint { name: "team", usage: " (list|add|remove|empty|join|leave|modify)" },
    CommandHint { name: "teammsg", usage: " <message>" },
    CommandHint { name: "tm", usage: " -> teammsg" },
    CommandHint { name: "teleport", usage: " (<location>|<destination>|<targets>)" },
    CommandHint { name: "tp", usage: " -> teleport" },
    CommandHint { name: "tellraw", usage: " <targets> <message>" },
    CommandHint { name: "test", usage: " (run|runmultiple|runthese|runclosest|runthat|runfailed|verify|locate|resetclosest|resetthese|resetthat|clearthat|clearthese|clearall|stop|pos|create)" },
    CommandHint { name: "tick", usage: " (query|rate|step|sprint|unfreeze|freeze)" },
    CommandHint { name: "time", usage: " (set|add|pause|resume|rate|query|of)" },
    CommandHint { name: "title", usage: " <targets> (clear|reset|title|subtitle|actionbar|times)" },
    CommandHint { name: "trigger", usage: " <objective> [add|set]" },
    CommandHint { name: "waypoint", usage: " (list|modify)" },
    CommandHint { name: "weather", usage: " (clear|rain|thunder)" },
    CommandHint { name: "worldborder", usage: " (add|set|center|damage|get|warning)" },
    CommandHint { name: "jfr", usage: " (start|stop)" },
    CommandHint { name: "ban-ip", usage: " <target> [<reason>]" },
    CommandHint { name: "banlist", usage: " [ips|players]" },
    CommandHint { name: "ban", usage: " <targets> [<reason>]" },
    CommandHint { name: "deop", usage: " <targets>" },
    CommandHint { name: "op", usage: " <targets>" },
    CommandHint { name: "pardon", usage: " <targets>" },
    CommandHint { name: "pardon-ip", usage: " <target>" },
    CommandHint { name: "perf", usage: " (start|stop)" },
    CommandHint { name: "save-all", usage: " [flush]" },
    CommandHint { name: "save-off", usage: "" },
    CommandHint { name: "save-on", usage: "" },
    CommandHint { name: "setidletimeout", usage: " <minutes>" },
    CommandHint { name: "stop", usage: "" },
    CommandHint { name: "transfer", usage: " <hostname> [<port>]" },
    CommandHint { name: "whitelist", usage: " (on|off|list|add|remove|reload)" },
];

pub struct Suggestion {
    pub ghost: String,
    pub accept: String,
}

pub fn suggest(input: &str) -> Option<Suggestion> {
    let trimmed_start = input.trim_start();
    if trimmed_start.is_empty() {
        return None;
    }

    let leading = &input[..input.len() - trimmed_start.len()];
    let slash = trimmed_start.starts_with('/');
    let body = if slash { &trimmed_start[1..] } else { trimmed_start };

    if body.contains(char::is_whitespace) {
        let cmd_part = body.split_whitespace().next().unwrap_or("");
        let hint = COMMANDS.iter().find(|c| c.name.eq_ignore_ascii_case(cmd_part))?;
        if body.len() == cmd_part.len() {
            let ghost = hint.usage.to_string();
            if ghost.is_empty() {
                return None;
            }
            return Some(Suggestion {
                ghost,
                accept: String::new(),
            });
        }
        return None;
    }

    let lower = body.to_ascii_lowercase();
    let matches: Vec<&CommandHint> = COMMANDS
        .iter()
        .filter(|c| c.name.starts_with(&lower))
        .collect();
    if matches.is_empty() {
        return None;
    }

    let best = *matches.iter().min_by_key(|c| c.name.len()).unwrap();
    if best.name == lower {
        if best.usage.is_empty() {
            return None;
        }
        return Some(Suggestion {
            ghost: best.usage.to_string(),
            accept: String::new(),
        });
    }

    let rest = &best.name[body.len()..];
    let mut accept = String::new();
    accept.push_str(rest);

    let mut ghost = String::new();
    ghost.push_str(rest);
    ghost.push_str(best.usage);

    let _ = (leading, slash);
    Some(Suggestion { ghost, accept })
}
