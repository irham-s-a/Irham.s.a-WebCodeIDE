import logoSrc from "components/logo/logo.png?inline";
import EditorFile from "lib/editorFile";

/**
 * Irham.s.a WebCode IDE (v0.5.0) - halaman beranda.
 * Tata letak dasbor: kartu hero, kisi aksi cepat, dan daftar alat.
 * Tidak ada tautan online, terminal, atau plugin.
 */
export default function openWelcomeTab() {
	const existingFile = editorManager.files.find((f) => f.id === "welcome-tab");
	if (existingFile) {
		existingFile.makeActive();
		return;
	}

	const welcomeFile = new EditorFile("Beranda", {
		id: "welcome-tab",
		render: true,
		type: "page",
		content: createWelcomeContent(),
		tabIcon: "icon acode",
		hideQuickTools: true,
	});

	welcomeFile.setCustomTitle(() => "Mulai");
}

function createWelcomeContent() {
	return (
		<div id="welcome-tab" className="welcome-page scroll">
			<section className="wc-hero">
				<div className="wc-hero-glow"></div>
				<img className="wc-hero-logo" src={logoSrc} width="72" height="72" alt="" />
				<h1>Irham.s.a WebCode IDE</h1>
				<p className="wc-hero-tagline">Editor kode offline untuk Android</p>
				<span className="wc-chip">
					v{BuildInfo.version} · {BuildInfo.versionCode}
				</span>
			</section>

			<section className="wc-block">
				<h2 className="wc-label">Mulai cepat</h2>
				<div className="wc-grid">
					<Tile
						tone="green"
						icon="add"
						title={strings["new file"]}
						onClick={() => acode.exec("new-file")}
					/>
					<Tile
						tone="cyan"
						icon="document-text-outline"
						title={strings["open file"]}
						onClick={() => acode.exec("open-file")}
					/>
					<Tile
						tone="amber"
						icon="folder_open"
						title={strings["open folder"]}
						onClick={() => acode.exec("open-folder")}
					/>
					<Tile
						tone="violet"
						icon="historyrestore"
						title={strings.recent}
						onClick={() => acode.exec("recent")}
					/>
				</div>
			</section>

			<section className="wc-block">
				<h2 className="wc-label">Alat</h2>
				<div className="wc-list">
					<Row
						icon="tune"
						title={strings["command palette"]}
						subtitle="Cari dan jalankan perintah"
						onClick={() => acode.exec("command-palette")}
					/>
					<Row
						icon="color_lenspalette"
						title={strings["change theme"]}
						subtitle="Hacker, Cyber, Terminal, dan lainnya"
						onClick={() => acode.exec("change-app-theme")}
					/>
					<Row
						icon="settings"
						title={strings.settings}
						subtitle="Editor, pratinjau, dan tampilan"
						onClick={() => acode.exec("open", "settings")}
					/>
					<Row
						icon="info_outline"
						title={strings.about}
						subtitle="Pengembang dan lisensi"
						onClick={() => acode.exec("open", "about")}
					/>
				</div>
			</section>

			<div className="wc-foot">Dibuat oleh Irham Satria Alfitra</div>
		</div>
	);
}

function Tile({ tone, icon, title, onClick }) {
	return (
		<div className={`wc-tile tone-${tone}`} onclick={onClick}>
			<span className={`icon ${icon}`}></span>
			<span className="wc-tile-title">{title}</span>
		</div>
	);
}

function Row({ icon, title, subtitle, onClick }) {
	return (
		<div className="wc-row" onclick={onClick}>
			<span className={`icon ${icon}`}></span>
			<div className="wc-row-text">
				<span className="wc-row-title">{title}</span>
				<span className="wc-row-sub">{subtitle}</span>
			</div>
			<span className="icon keyboard_arrow_right"></span>
		</div>
	);
}
