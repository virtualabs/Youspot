<?php

/***********************************

Youspot Webservice (dirty version)

***********************************/

require_once('Services/JSON.php');
$_json = new Services_JSON();

/* Connect to the database */
mysql_connect("localhost","YOUR_USERNAME_HERE","YOUR_PASSWORD_HERE");
mysql_select_db("youspot");

/* Client functions */
function isClientRegistered($token) {
    $token = T($token);
    $sql = "SELECT timeout from no3g_clients WHERE `token_id`='$token'";
    $res = mysql_query($sql);
    if (mysql_num_rows($res)==1)
        return (timeout>time());
    return false;
}

/* French well-known captive portals */
function isHotspot($essid) {
	return (in_array(strtoupper($essid),array('FREE PUBLIC WIFI','FREEWIFI','NEUF WIFI','SFR WIFI PUBLIC','ORANGE')));
}

function canConnect($cipher, $key)
{
	return ( ($cipher!='OPEN' && $key!='') || ($cipher=='OPEN') );
}

function T($d)
{
	return mysql_real_escape_string($d);
}

function __log($msg)
{
	$h = fopen("no3g.txt","w+");
	fwrite($h, $msg);
	fwrite($h, "\n");
	fclose($h);
}

function json_response($status, $data)
{
	global $_json;
	$resp = array('status'=>(int)$status);
	foreach($data as $key=>$value)
		$resp[$key] = $value;
	die($_json->encode($resp));
}

function json_register_response($answer,$spots)
{
	die(json_response(200,array("answer"=>$answer, "spots"=>$spots)));
}

function json_map_response($answer, $lat, $lng, $spots)
{
	die(json_response(200,array("answer"=>$answer, "spots"=>$spots,"latitude"=>$lat,"longitude"=>$lng)));
}

function add_geoloc($spot)
{
		$bssid = T($spot->{BSSID});
		$lng = (float)$spot->{LNG};
		$lat = (float)$spot->{LAT};
		
		$sql = "UPDATE no3g_spots SET `lng`=$lng, `lat`=$lat,`geo`='1' WHERE bssid='$bssid'";
		$res = mysql_query($sql);
	    json_response(200,array());
}

function register_spots($spots) {
	global $_json;
	global $nonfree;
	
	$known_spots = array();	
	
	foreach($spots as $spot)
	{
		$essid = T($spot->SSID);
		$bssid = T($spot->BSSID);
		$enc = T($spot->ENC);
		$key = T($spot->KEY);
		
		/* Check if the spot is already registered */		
		$sql = "SELECT * FROM no3g_spots WHERE ssid='$essid' AND bssid='$bssid'";
		$res = mysql_query($sql);
		if (mysql_num_rows($res)==0)
		{
			/* if not, register in */
			$now = time();
			$sql = "INSERT INTO no3g_spots VALUES ('','$essid','$bssid','$enc','0','$key','0.0','0.0','0','$now','0')";
			mysql_query($sql);
			
			/*
			 * ... and add it to the registered networks if:
			 * - network is OPEN
			 * - network is passwd protected by the key is known
			 */
			 
			if (canConnect($enc,$key) && !isHotspot($essid))
			{
				array_push($known_spots,
					array(
						'SSID'=>$essid,
						'BSSID'=>$bssid,
						'ENC'=>$enc,
						'KEY'=>$key,
					)
				);
			}
		}
		else
		{
			/* if already present, then extract it and add it to the response */
			$dbspot = mysql_fetch_array($res);
			
			/* Check if key is set */
			if (!empty($key))
			{
			    $sql = "UPDATE `no3g_spots` SET `key`='$key' WHERE `ssid`='$essid' AND `bssid`='$bssid'";
			    mysql_query($sql);
			    $dbspot['key'] = $key; 
			}
					
			/* Keep only open networks and closed networks with known key */
			if (canConnect($dbspot['cipher'],$dbspot['key']) && !isHotspot($dbspot['ssid']) )		
			{
				array_push($known_spots,
					array(
						'SSID'=>$dbspot['ssid'],
						'BSSID'=>$dbspot['bssid'],
						'ENC'=>$dbspot['cipher'],
						'KEY'=>$dbspot['key'],
					)
				);
			}
		}
	}
	/* send the result */
	json_register_response(true, $known_spots);
}

function find_spots($latitude, $longitude)
{
	$known_spots = array();	
	$sql = "SELECT lat,lng, ( 6371 * acos( cos( radians($latitude) ) * cos( radians( lat ) ) * cos( radians( lng ) - radians($longitude) ) + sin( radians($latitude) ) * sin( radians( lat ) ) ) ) AS distance FROM no3g_spots WHERE ssid not like 'Freewifi%' and ssid not like 'SFR%wifi%' and ssid not like 'orange%' and ssid not like 'neuf%wifi%' and (cipher='OPEN' or (cipher<>'OPEN' and `key`<>'')) HAVING distance < 1  ORDER BY distance;";
    $res = mysql_query($sql);
    while($spot = mysql_fetch_array($res))
    {
        array_push($known_spots,
            array(
                'LAT'=>(float)$spot['lat'],
                'LNG'=>(float)$spot['lng']
            )
        );
    }
    
    json_map_response(true, $latitude, $longitude, $known_spots);
}


function login($userid, $version)
{
    $userid = T($userid);

   /* check if we have a new version */
    $sql = "SELECT * FROM no3g_versions ORDER BY version DESC";
    $res = mysql_query($sql);
    if (mysql_num_rows($res)>0)
    {
        $last_version = mysql_fetch_array($res);
        if ($last_version['version']>$version)
        {
            $changelog = $last_version['changelog'];
            $changelog = str_replace("\r","",$changelog);
            json_response(200, array('need_upgrade'=>true, 'url'=>stripslashes('http://virtualabs.fr/no3g/'.$last_version['file']), 'changelog'=>$changelog, 'token'=>'','userid'=>''));
        } 
    }

   /* check if user is already registered */
   $sql = "SELECT * from no3g_clients WHERE `client_id`='$userid'";
   $res = mysql_query($sql);
   if (mysql_num_rows($res)==1)
   {
       /* get user info */
       $user = mysql_fetch_array($res);
       
       /* renew his/her token */
       $token = md5(md5(rand().$user['client_id'].rand()));
       $timeout = time()+3600; /* session lifetime: 1 hour */
       $ip = $_SERVER['REMOTE_ADDR'];
       $sql = "UPDATE no3g_clients SET `ip`='$ip',`token_id`='$token', `timeout`='$timeout' WHERE `client_id`='$userid'";
       mysql_query($sql);
   }
   else
   {
       /* register our new client with a brand new token*/
       $userid = md5(md5(rand().'NO3GROXX'.rand()));
       $token = md5(md5(rand().$userid.rand()));
       $timeout = time()+3600; /* session lifetime: 1 hour */
       $ip = $_SERVER['REMOTE_ADDR'];

       $sql = "INSERT INTO no3g_clients VALUES ('','$userid','$token','$timeout','$ip')";
       mysql_query($sql);
   }
   json_response(200,array('need_upgrade'=>false, 'url'=>'', changelog=>'', 'token'=>$token, 'userid'=>$userid));
}

if(!empty($_POST['object']))
{
	$object = $_json->decode(stripslashes($_POST['object']));
	switch($object->action)
	{
		case 'register_spot':
		{
			$spots = $object->spot;
			register_spots($spots);
		}
		break;
		
		case 'geoloc':
		{
		    $spot = $object->spot;
		    add_geoloc($spot);
		}
		break;
        
        /* A desactiver apres param des coordonnees */		
		case 'export':
		{
		    export_coords_needed();
		}
		break;
		
		case 'map_spots':
		{
		    find_spots((float)$object->latitude, (float)$object->longitude);
		}
		break;

        case 'login':
        {
            login($object->userid,$object->version);
        }
        break;
		
		default:
			die();			
	}
}

?>
