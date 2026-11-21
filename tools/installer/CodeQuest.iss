; Inno Setup script for the CodeQuest installer.
; Build: see tools/installer/build-installer.ps1 (it builds the jar, makes the app folder with jpackage, then runs this).
#define AppName "CodeQuest"
#define AppVersion "1.0.0"
#define Publisher "The Wolves"
#ifndef SourceDir
  #define SourceDir "..\..\build\installer\image\CodeQuest"
#endif
#ifndef OutDir
  #define OutDir "..\..\build\installer\out"
#endif

[Setup]
AppId={{6B1B0E52-7C3A-4C58-9E0D-C0DE9E57A11D}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#Publisher}
; installs for the current user only, so it needs no administrator password
PrivilegesRequired=lowest
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir={#OutDir}
OutputBaseFilename=CodeQuest-Setup-{#AppVersion}
SetupIconFile=..\..\lwjgl3\icons\logo.ico
UninstallDisplayIcon={app}\CodeQuest.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\CodeQuest.exe"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\CodeQuest.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\CodeQuest.exe"; Description: "Start CodeQuest"; Flags: nowait postinstall skipifsilent
