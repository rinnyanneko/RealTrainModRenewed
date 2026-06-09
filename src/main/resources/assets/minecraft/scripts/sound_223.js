//include <scripts/LibSound.js>

function onUpdate(su){

	var notch = su.getNotch();
	var speed = su.getSpeed();
	var maxSp = 120;
	var moving = speed > 1.0;
	var powering = notch > 0;

	su.stopSound("rtm", "train.223_s0");
	su.stopSound("rtm", "train.223_s1");
	su.stopSound("rtm", "train.223_s2");

	if(!moving){
		su.stopSound("rtm", "train.223_run");
		su.stopSound("rtm", "train.223_run_tunnel");
		su.playSound("rtm", "train.223_air", 1.0, 1.0);
	}else{
		su.stopSound("rtm", "train.223_air");
		var vol = speed / 80.0;
		if(vol < 0.15) vol = 0.15;
		if(vol > 1.0) vol = 1.0;
		if(!powering) vol = vol * 0.7;
		var pitch = (speed / maxSp) * 0.35 + 0.85;
		if(pitch > 1.35) pitch = 1.35;
		if(su.inTunnel()){
			su.stopSound("rtm", "train.223_run");
			su.playSound("rtm", "train.223_run_tunnel", vol, pitch);
		}else{
			su.stopSound("rtm", "train.223_run_tunnel");
			su.playSound("rtm", "train.223_run", vol, pitch);
		}
	}
}
