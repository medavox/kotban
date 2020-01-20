git-compatible kanban board
==========================

A kanban board which can be stored in git.

Changes to the board are reflected sanely in commits.

simply:

```
board
├── 1new
│   └── new_idea.md
├── 2in-progress
│   └── idea_being_worked_on.md
├── 3implemented
│   └── good_idea.md
└── 4discarded
    └── bad_idea.md
```

* each subdir of `board` is a list
* lists are displayed in alphabetical order, so preface them with numbers if you want a specific order
* each card in a list is a text file in the subdir
* files can be plain text or markdown
