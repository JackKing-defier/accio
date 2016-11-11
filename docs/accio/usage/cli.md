---
layout: accio
nav: accio
title: Command line reference
---

Accio comes with a command line interface allowing to run experiments and analyze their results.

* TOC
{:toc}

## Running experiments

The `accio run` command is used to launch experiments.
It requires one or many arguments specifying paths to files containing experiment definitions.
These files must be JSON files formatted according to the [definition language](workflows.html).
You can specify files containing either a experiment definition or a workflow definition (which will be implicitly converted into an experiment).

### Experiment execution

Experiments are ran locally, in the same process, using all available cores of your machine.
It means that you must be careful to not interrupt the process until it completed.
If you specify several files, they will be launched sequentially.

### Options for `accio run`

`-workdir=/path/to/directory`

The working directory is the place where the reports generated by experiments will be stored.
By default, a temporary directory will be created and advertised in the console output, but you can specify a specific directory.
Outputs of several experiments can be stored in the same directory, names of files storing reports are generated in such a way to avoid name clashes.

`-name="My experiment name"`

Overrides the experiment name at runtime, when launching it.
It will replace the value specified in the definition file.

`-tags="tag1 tag2"`

Overrides the experiment tags at runtime, when launching it.
Tags are space-separated, heading or trailing whitespaces are ignored.
It will replace the value specified in the definition file.

`-notes="Some notes to remember why I ran this experiment"`

Overrides the experiment notes at runtime, when launching it.
It will replace the value specified in the definition file.

`-runs=3`

Overrides the experiment number of runs at runtime, when launching it.
It will replace the value specified in the definition file.

`-user="John Doe <john.doe@gmail.com>"`

Specifies the user who ran the experiment.
It can include an email address between chevrons.
If this option is not specified, the person who launched an experiment is automatically recorded by using two sources:

  * The environment variable `ACCIO_USER`, which contains the user in the same allowed format.
  * The current shell user, in which case no email address can be inferred.

`-params="node1/epsilon=0.002 node2/distance=30.meters"`

Overrides some parameters at runtime, when launching the experiment.
Parameters are space-separated. Parameter names are references, written in the same manner than inside [workflow definitions](/definition-language.html).
It will take precedence over parameters defined inside a workflow, but execution strategies may still override them later (e.g., if this parameter is being optimized).


## Built-in documentation

Accio comes with built-in documentation.

  * `accio help` enumerates all available commands and their options.
  * `accio help <command>` provides detailed information about a given command.
  * `accio help list-ops` enumerates all available operators, their inputs and outputs.
  * `accio help <operator>` provides detailed information about a given operator.

## `.acciorc`, the Accio configuration file

Accio accepts many options.
While some of them are frequently varying (e.g., the name of an experiment), some others are less susceptible to vary (e.g., your user name).
To prevent you from typing again and again the same options each time you use Accio, you can specify them once for all in a configuration file called `.acciorc`.

Accio looks for a configuration file at the path specified by the `-acciorc` option.
If not specified, Accio looks for a `.acciorc` in the current directory where launched Accio, then for `~/.acciorc` and finally for `/etc/accio.acciorc`.
The first file found will be used, the other ones, even if existing, will not be considered.
Consequently, specifying `-acciorc=/dev/null` will disable any configuration file parsing.

`.acciorc` files are text files following a simple line-based grammar.
Lines starting with a `#` are considered as comments and ignored.
Blank lines are ignored.
Each line starts with a command name then followed by a list of options that will be appended to any execution of this particular command.
It means the first word must necessarily by the name of an Accio command, such as `run` or `export`.
Many lines can be used for the same command, they will be accumulated (the last lines taking precedence over the previous ones).
Moreover, options specified through the command line always take precedence over those coming from a configuration file.

In configuration files, commands names may suffixed by `:config`, `config` being a tag for a particular configuration.
These options are ignored by default, but can be included with the `-config` option.
The goal of this is to package command line options that work together.
For example, a `run:twice -runs=2` line in a configuration file combined with an `accio run -config=twice /path/to/workflow.json` Accio invocation in running twice any workflow.

The `-acciorc` and `-config` options must appear *before* the command name (e.g., `run`).